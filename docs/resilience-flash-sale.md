# Resilience & Flash sale — chịu tải spike trên hạ tầng cố định

> **Mục đích:** gom các cơ chế chịu lỗi/chịu tải nằm rải ở gateway, payment, waiting room
> thành một bức tranh. Một phần đã chạy (gateway), phần còn lại là định hướng. Ràng buộc
> gốc: [`/CLAUDE.md`](../CLAUDE.md). Liên quan: [`inventory-no-oversell.md`](./inventory-no-oversell.md),
> [`payment-stripe-flow.md`](./payment-stripe-flow.md), [`waiting-room.md`](./waiting-room.md).

---

## 1. Ràng buộc chi phối: on-prem KHÔNG autoscale
Phần cứng **cố định**, không "thêm máy" lúc spike. Hệ quả thiết kế:
- **Sizing cho throughput BỀN VỮNG**, không cho đỉnh tức thời.
- **Waiting Room hấp thụ spike** — thả người vào theo nhịp hạ nguồn chịu được.
- Mọi tầng phải **bảo vệ tài nguyên hữu hạn**: từ chối/chờ có kiểm soát còn hơn đổ sập.

## 2. Bốn lớp phòng thủ

```
   chục nghìn user
        │
   [1] Waiting Room ── thả theo nhịp (Redis sorted set; biết tồn kho + ngưỡng Stripe)
        │
   [2] API Gateway ── rate limiter (Redis token bucket) + circuit breaker (Resilience4j)
        │
   [3] Service ── timeout, bulkhead, retry có kiểm soát
        │
   [4] Payment ── rate limiter + retry backoff cho ngưỡng 429 của Stripe
```

## 3. Lớp 2 — Gateway (ĐÃ chạy)
Code thật: `apigateway/config/GatewayConfig.java` + `application.properties`.
- **Rate limiter** (`RequestRateLimiter`, backend Redis token-bucket): mỗi route khai
  `replenishRate` (req/s bền vững) + `burstCapacity` (đỉnh tức thời). Key theo **user (JWT
  sub)**, chưa đăng nhập thì theo **IP** (`clientKeyResolver`). Route tranh chấp/nhạy cảm
  (auth, order, payment) đặt rate THẤP.
- **Circuit breaker** (Resilience4j): service hạ nguồn lỗi/chậm quá ngưỡng → **mở mạch**,
  trả `fallbackUri=/__fallback` (503) ngay thay vì để request dồn ứ. Mặc định: timeout 3s,
  slidingWindow 20, failureRate 50%, mở 10s.

> ⚠️ Rate-limit theo IP chỉ đúng khi IP client KHÔNG giả mạo được — xem cấu hình
> `X-Forwarded-For` (ghi đè bằng IP thật) trong `infra/nginx/nginx.conf`.

## 4. Lớp 4 — Payment ↔ Stripe (định hướng)
Stripe ~100–200 req/s/tài khoản, vượt = 429. Payment **bắt buộc**:
- **Rate limiter** cục bộ để KHÔNG vượt ngưỡng.
- **Retry exponential backoff** (Resilience4j) xử lý 429 sạch — backoff + jitter, có trần
  số lần; **không retry dồn dập** (làm ngưỡng tệ hơn).
Chi tiết: [`payment-stripe-flow.md`](./payment-stripe-flow.md) §3.

## 5. Lớp 1 — Waiting Room
Van chặn trước spike: hàng đợi Redis sorted set, thả người theo nhịp mà Inventory + Stripe
chịu được, kèm CAPTCHA chống bot. **Admission rate phải biết tồn kho còn lại** (đừng thả
nhiều hơn vé). Thiết kế: [`waiting-room.md`](./waiting-room.md).

## 6. Lớp 3 — trong service (định hướng)
- **Timeout** mọi lời gọi ra ngoài (đừng chờ vô hạn → cạn thread/kết nối).
- **Bulkhead**: tách pool tài nguyên cho lời gọi chậm (vd Stripe) khỏi phần còn lại.
- **Retry có kiểm soát**: chỉ retry lỗi tạm thời + idempotent; backoff + jitter; có trần.
- **Fail fast** khi mạch mở/quá tải, trả lỗi nhã nhặn (503 + Retry) thay vì treo.

## 7. Quan sát để chỉnh (observability)
Không có số đo thì không sizing đúng. Đã có khung: Prometheus + Grafana, tracing
OpenTelemetry (Micrometer), log Loki/ELK (xem `*/application.properties`
`management.*`). Theo dõi: p99 latency mỗi bước, tỉ lệ 429/5xx, độ sâu hàng đợi Waiting
Room, tồn kho còn lại — để cân `replenishRate`, TTL hold, admission rate.

## 8. Nguyên tắc
- **Throughput bền vững > đỉnh.** Hấp thụ spike bằng hàng đợi, không bằng "cố phục vụ hết".
- **Bảo vệ tài nguyên hữu hạn** ở mọi tầng (cố định, không autoscale).
- **Từ chối/chờ có kiểm soát** (429/503 + Retry) còn hơn đổ sập dây chuyền.
- Mọi retry phải **idempotent + có backoff + có trần**.
