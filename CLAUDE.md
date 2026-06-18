# Hệ thống bán vé sự kiện âm nhạc

## Tổng quan
Nền tảng bán vé concert quy mô lớn. Đặc thù: **flash sale** — chục nghìn người
truy cập cùng lúc khi mở bán, tranh nhau tồn kho hữu hạn, tuyệt đối không được
bán trùng. Thanh toán qua Stripe.

## Ràng buộc kiến trúc (bất biến)
- Kiến trúc **microservices**, triển khai **on-premise** (không dùng managed
  cloud service), Spring Boot (JVM).
- **On-prem không autoscale** → phần cứng cố định, sizing cho throughput bền
  vững; Waiting Room hấp thụ spike. Đây là ràng buộc chi phối thiết kế.
- **Database-per-service**: mỗi service sở hữu DB riêng (PostgreSQL). KHÔNG dùng
  chung một DB.
- Không có ACID transaction xuyên service → dùng **Saga pattern (orchestration)**
  cho luồng mua vé, kèm bước bù trừ (compensating transaction).
- Đảm bảo phát event không mất bằng **transactional outbox**.
- **Phạm vi hiện tại**: Stripe dùng **một tài khoản duy nhất**. CHƯA dùng Connect,
  CHƯA tách Ledger service. Khi cần chia tiền nhiều bên mới thêm sau.

## Stack
- Backend: Spring Boot
- Gateway: Spring Cloud Gateway (validate JWT tại gateway)
- Service discovery + config: Consul hoặc Kubernetes DNS
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
- Webhook Stripe phải verify chữ ký, idempotent, đẩy vào Kafka rồi mới xử lý.
- Stripe phải gọi ngược được vào webhook endpoint → cần reverse proxy ở DMZ.

## Danh sách service

### Hạ tầng (Phase 0 — dựng trước)
API Gateway, Service Discovery + Config, cụm PostgreSQL/Redis/Kafka,
observability stack.

### 1. Auth/User service
Đăng ký, đăng nhập, phát JWT. Gateway dùng token để xác thực mọi request.
Sở hữu user, credential, role. DB: PostgreSQL. Giai đoạn đầu giữ gọn.

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
Cổng **duy nhất** gọi ra Stripe (một tài khoản, Payment Intents). Xử lý webhook,
đặt rate limiter/circuit breaker. Sở hữu bản ghi thanh toán, idempotency key,
tham chiếu Stripe. DB: PostgreSQL. Mọi bước idempotent xuyên service.

### 6. Ticket service
Vé thật đã phát ra sau thanh toán (khác ticket type ở Catalog). Sinh QR code
**có ký số**, validate khi quét tại cổng. Consume event "đơn hoàn tất" từ Kafka.
Sở hữu vé đã phát + trạng thái sử dụng. DB: PostgreSQL.

### 7. Notification service
Gửi email/SMS (xác nhận đơn, đính kèm vé). Gần như stateless, consume từ Kafka.

### 8. Waiting Room service
Van bảo vệ trước spike. Hàng đợi Redis sorted set, thả người vào theo nhịp hạ
nguồn (và Stripe) chịu được, kèm CAPTCHA chống bot. Admission rate phải biết về
tồn kho còn lại. Store chính: Redis. Làm CUỐI CÙNG.

## Thứ tự triển khai
1. Phase 0: hạ tầng dùng chung.
2. Auth (1) + Catalog (2) — mở khóa phần còn lại.
3. Lát cắt dọc happy path: Inventory (3, chỉ GA) → Payment (5, một tài khoản) →
   Order (4). Cột mốc: tiền chạy được end-to-end.
4. Đắp thịt: mở rộng Inventory cho ghế ngồi, thêm Ticket (6) + Notification (7)
   chạy async qua Kafka.
5. Waiting Room (8) — khi đã có luồng hoàn chỉnh để throttle.

## Nguyên tắc làm việc
- Dựng "walking skeleton" (luồng mua xuyên suốt tối giản) trước, hoàn thiện sau.
- Hoãn hai mảnh khó nhất (Connect, Waiting Room) đến khi luồng lõi đã chạy.
- Phân biệt rõ "vé như sản phẩm" (ticket type, Catalog) với "vé đã phát"
  (issued ticket, Ticket service) — đừng để hai khái niệm dính vào nhau.
