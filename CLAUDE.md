# Hệ thống bán vé sự kiện âm nhạc

## Tổng quan
Nền tảng bán vé concert quy mô lớn. Đặc thù: **flash sale** — chục nghìn người
truy cập cùng lúc khi mở bán, tranh nhau tồn kho hữu hạn, tuyệt đối không được
bán trùng. Thanh toán qua Stripe.

## Ràng buộc kiến trúc (bất biến)
- Kiến trúc **microservices**, triển khai **on-premise** trên **Kubernetes thuần
  tự quản** (không dùng managed cloud service), Spring Boot (JVM).
- **On-prem không autoscale** → phần cứng cố định, sizing cho throughput bền
  vững; Waiting Room hấp thụ spike. Đây là ràng buộc chi phối thiết kế.
- **Database-per-service**: mỗi service sở hữu DB riêng (PostgreSQL). KHÔNG dùng
  chung một DB.
- Không có ACID transaction xuyên service → dùng **Saga pattern (orchestration)**
  cho luồng mua vé, kèm bước bù trừ (compensating transaction).
- Đảm bảo phát event không mất bằng **transactional outbox**.
- Stripe dùng **một tài khoản duy nhất** (Payment Intents); **không** chia tiền
  nhiều bên (không Connect, không Ledger service).

## Stack
- Backend: Spring Boot
- Gateway: Spring Cloud Gateway — **một service riêng** (`apigateway/`), chạy
  **WebFlux/Netty thuần, TUYỆT ĐỐI KHÔNG add JPA/JDBC/DB** (blocking sẽ chẹn
  event-loop lúc flash sale). Validate JWT tại gateway (chỉ verify chữ ký bằng
  public key, không gọi Auth service mỗi request).
- Service discovery + config: **K8s DNS + ConfigMap/Secret** (đã chọn K8s thuần
  → KHÔNG dùng Consul). Service gọi nhau qua DNS nội bộ `http://<svc>:<port>`.
- Hot path / cache / khóa: Redis (cluster hoặc Sentinel cho HA)
- Nguồn sự thật: PostgreSQL (mỗi service một DB, có replication)
- Message broker: Kafka (self-hosted)
- Resilience: Resilience4j (circuit breaker, retry backoff, rate limiter)
- Observability: Prometheus + Grafana, tracing OpenTelemetry, log Loki/ELK
- Tracing trong app: Micrometer Tracing

## Giới hạn Stripe cần tôn trọng
- ~100-200 request/giây mỗi tài khoản. Lỗi vượt ngưỡng = HTTP 429.
- Payment service phải có rate limiter + retry exponential backoff (Resilience4j)
  để không vượt ngưỡng và xử lý 429 sạch sẽ.
- Thu tiền là **bất đồng bộ**: client xác nhận PaymentIntent qua **Payment
  Element**, kết quả `succeeded`/`failed` chốt từ **webhook** (nguồn sự thật).
- Webhook Stripe phải verify chữ ký, idempotent, đẩy vào Kafka rồi mới xử lý.
- Stripe phải gọi ngược được vào webhook endpoint → cần reverse proxy ở DMZ.

## Danh sách service

> **Trạng thái:** các service đã có project — `apigateway/`, `auth/`, `catalog/`,
> `inventory/`, `order/`, `payment/`, `ticket/`, `notification/`, `waitingroom/`.
> Mọi thành phần dưới đây đều trong phạm vi.

### Hạ tầng dùng chung (dựng trên K8s qua Operator/Helm, không phải project trong repo)
Cụm PostgreSQL/Redis/Kafka (Operator: CloudNativePG, Redis Operator, Strimzi),
Ingress Controller + MetalLB (on-prem không có cloud LB), observability stack
(Prometheus/Grafana/Loki, OpenTelemetry). Webhook Stripe vào qua **Ingress rule
riêng** trỏ thẳng Payment service, **bỏ qua apigateway** (đây là "reverse proxy
DMZ"), verify chữ ký Stripe chứ không phải JWT.

### 0. API Gateway service (`apigateway/`)
Cửa vào duy nhất cho traffic nghiệp vụ. Spring Cloud Gateway (WebFlux). Route
theo path → service qua K8s DNS. Validate JWT, rate limit ở biên (Redis),
circuit breaker (Resilience4j). KHÔNG có DB, KHÔNG business logic. Đứng sau
Ingress.

### 1. Auth/User service
Đăng ký, đăng nhập, phát JWT. Gateway dùng token để xác thực mọi request.
Sở hữu user, credential, role. DB: PostgreSQL.

### 2. Catalog service
Trả lời "có gì để bán": sự kiện, địa điểm, seat map, ticket type (loại vé), giá.
Đọc nhiều ghi ít → cache mạnh (Redis), seat map tĩnh qua CDN/Nginx.
KHÔNG giữ số lượng tồn (đó là Inventory). DB: PostgreSQL.

### 3. Inventory service
Trả lời "còn bao nhiêu, ghế nào trống". Service tranh chấp cao nhất.
- GA: counter Redis `DECRBY`.
- Ghế ngồi: seat hold bằng `SET NX` + TTL.
- Trạng thái SOLD ghi bền xuống PostgreSQL (nguồn sự thật).

### 4. Order service
Trái tim điều phối — **Saga orchestrator**. Tạo đơn, gọi Inventory giữ chỗ, gọi
Payment thu tiền, xác nhận SOLD + phát event sinh vé khi thành công; chạy bù trừ
khi lỗi. Sở hữu đơn hàng + trạng thái saga. Dùng outbox. DB: PostgreSQL.

### 5. Payment service
Cổng **duy nhất** gọi ra Stripe (một tài khoản, Payment Intents; Payment Element
phía client). Thu tiền bất đồng bộ: khởi tạo PaymentIntent → client xác nhận →
**settle qua webhook**. Đặt rate limiter/circuit breaker. Sở hữu bản ghi thanh
toán, idempotency key, tham chiếu Stripe. DB: PostgreSQL. Mọi bước idempotent
xuyên service.

### 6. Ticket service
Vé thật đã phát ra sau thanh toán (khác ticket type ở Catalog). Sinh QR code
**có ký số**, validate khi quét tại cổng. Consume event "đơn hoàn tất" từ Kafka.
Sở hữu vé đã phát + trạng thái sử dụng. DB: PostgreSQL.

### 7. Notification service
Gửi email/SMS (xác nhận đơn, đính kèm vé). Gần như stateless, consume từ Kafka.

### 8. Waiting Room service
Van bảo vệ trước spike. Hàng đợi Redis sorted set, thả người vào theo nhịp hạ
nguồn (và Stripe) chịu được, kèm CAPTCHA chống bot. Admission rate phải biết về
tồn kho còn lại. Store chính: Redis.

## Nguyên tắc làm việc
- Phân biệt rõ "vé như sản phẩm" (ticket type, Catalog) với "vé đã phát"
  (issued ticket, Ticket service) — đừng để hai khái niệm dính vào nhau.
- Mọi luồng tiền/vé phải idempotent xuyên service; webhook Stripe là nguồn sự
  thật cho "đã thanh toán hay chưa".
