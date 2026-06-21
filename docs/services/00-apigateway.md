# 0. API Gateway (`apigateway/`)

## Trách nhiệm
Cửa vào **duy nhất** cho traffic nghiệp vụ. Spring Cloud Gateway (**WebFlux/Netty**).
Route theo path → service qua K8s DNS. Xác thực JWT ở biên, rate limit, circuit
breaker. **KHÔNG DB, KHÔNG JPA/JDBC, KHÔNG business logic** (blocking sẽ chẹn
event-loop lúc flash sale).

## Không có database
State duy nhất là **Redis** cho rate limiter ở biên.

| Key | Kiểu | TTL | Ý nghĩa |
|---|---|---|---|
| `rl:{routeId}:{sub|ip}` | counter | cửa sổ giây | token-bucket / sliding window theo route + danh tính |

## Xác thực JWT (chỉ verify chữ ký)
- Lấy **public key** từ Auth `GET /internal/jwks` **một lần**, cache (refresh theo
  `kid`/định kỳ). KHÔNG gọi Auth mỗi request.
- Verify chữ ký + `exp`; trích `sub` (userId), `role`. Sai/thiếu → **401** trước khi
  vào service.
- Truyền danh tính xuống service (header tin cậy nội bộ hoặc forward token).

## Bảng route (path → service)
| Path prefix | Service:port | Quyền ở biên |
|---|---|---|
| `/api/auth/public/**` | auth:8081 | mở |
| `/api/auth/**` | auth:8081 | JWT |
| `/api/catalog/public/**` | catalog:8082 | mở |
| `/api/catalog/admin/**` | catalog:8082 | JWT (role check ở service) |
| `/api/catalog/**` | catalog:8082 | JWT |
| `/api/order/**` | order:8084 | JWT |
| `/api/ticket/**` | ticket:8087 | JWT |
| `/api/waitingroom/public/**` | waitingroom:8089 | mở (+CAPTCHA) |
| `/internal/**` | — | **KHÔNG route** (chặn ở biên) |
| `/webhooks/**` | — | **KHÔNG route** (đi qua DMZ thẳng Payment) |

## Resilience (Resilience4j)
- **Rate limiter** ở biên (Redis) theo route — van đầu tiên trước flash sale.
- **Circuit breaker** mỗi route đích; mở mạch → `/__fallback` trả **503** gọn
  (không treo event-loop).
- Timeout ngắn cho lời gọi hạ nguồn.

## API nội bộ của gateway
| Method | Path | Mục đích |
|---|---|---|
| ANY | `/__fallback` | trả 503 khi circuit breaker mở |

## Lưu ý triển khai
- TUYỆT ĐỐI không thêm starter JPA/JDBC vào module này.
- `/internal/**` không có trong bảng route ⇒ gọi từ ngoài → 404 (không lộ).
- Webhook Stripe KHÔNG qua gateway (xem Payment + `deployment-k8s.md`).
