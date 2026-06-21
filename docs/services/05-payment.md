# 5. Payment service (`payment/`) — Stripe THẬT, không mock

> Sâu hơn: [`../payment-stripe-flow.md`](../payment-stripe-flow.md) (đa phương thức) · [`../payment_issue.md`](../payment_issue.md) (cạm bẫy) · [`../impl/01-payment-real-stripe.md`](../impl/01-payment-real-stripe.md) (spec code).

## Trách nhiệm
Cổng **DUY NHẤT** ra Stripe (một tài khoản, **Payment Intents**). Thu tiền **bất
đồng bộ**: tạo intent → client xác nhận bằng **Payment Element** → **webhook là
nguồn sự thật** cho succeeded/failed. Sở hữu bản ghi thanh toán, idempotency,
tham chiếu Stripe. KHÔNG có `MockPaymentGateway`. Chi tiết triển khai:
[`../impl/01-payment-real-stripe.md`](../impl/01-payment-real-stripe.md).

## Database `payment`

### payments
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| order_id | UUID | NOT NULL **UNIQUE** (1 payment/đơn — idempotency lớp DB) |
| amount_minor | BIGINT | NOT NULL CHECK ≥ 0 |
| currency | VARCHAR(3) | NOT NULL |
| status | VARCHAR(32) | NOT NULL — `PENDING\|PROCESSING\|SUCCEEDED\|FAILED\|CANCELED` |
| stripe_pi_id | VARCHAR(255) | NULL — PaymentIntent (`pi_...`), `ix_payments_pi` |
| payment_ref | VARCHAR(255) | NULL — tham chiếu charge/latest |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

> `client_secret` **KHÔNG lưu DB** — bí mật ngắn hạn, chỉ trả client 1 lần.

### processed_events  (idempotency webhook)
| Cột | Kiểu | Ghi chú |
|---|---|---|
| event_id | VARCHAR(255) | PK — Stripe `evt_...` |
| event_type | VARCHAR(120) | NOT NULL |
| processed_at | TIMESTAMPTZ | NOT NULL |

### outbox  (khuôn chuẩn — phát `PaymentSettled` → `payment.events`)

## API — internal (Order gọi)
| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/internal/payment-intents` | `{orderId, amountMinor, currency}` | `{paymentId, stripePiId, clientSecret, status:"PROCESSING"}` |
| GET | `/internal/payments/by-order/{orderId}` | – | `{paymentId,orderId,status,stripePiId,amountMinor,currency}` · 404 |

Tạo intent: `PaymentIntent.create` với `automatic_payment_methods.enabled=true`,
`metadata.order_id`, header `Idempotency-Key="order:<orderId>"`. **KHÔNG** phát
PaymentSettled ở bước này (chỉ webhook mới phát).

## API — webhook (Stripe gọi qua DMZ, KHÔNG JWT)
| Method | Path | Header |
|---|---|---|
| POST | `/webhooks/stripe` | `Stripe-Signature` |

Quy trình (1 `@Transactional`):
1. **Verify chữ ký + timestamp** (`stripe.webhook-secret`).
2. **Idempotent**: `processed_events.existsById(event.id)` → bỏ qua.
3. Map `metadata.order_id` → payment. **Đối chiếu amount/currency** với đơn (chống thao túng).
4. `payment.settle(status, piId)` + outbox `PaymentSettled` + lưu processed_events.
5. Trả **2xx nhanh** (Stripe coi non-2xx là fail → retry).

Event xử lý:
- **Thẻ:** `payment_intent.succeeded`→SUCCEEDED · `payment_intent.payment_failed`→FAILED
  · `payment_intent.canceled`→CANCELED.
- **Async (Konbini/Furikomi):** `checkout.session.async_payment_succeeded`→SUCCEEDED ·
  `checkout.session.async_payment_failed`→FAILED · `payment_intent.processing`→PROCESSING.

Body lấy **raw** để verify đúng bytes. Đơn async để `AWAITING_PAYMENT` vài giờ→ngày là
bình thường (TTL hold theo phương thức — xem [`../inventory-no-oversell.md §3.1`](../inventory-no-oversell.md)).

## Map PaymentIntent.status → PaymentStatus
| Stripe | PaymentStatus | Phát settled? |
|---|---|---|
| requires_payment_method / requires_confirmation / requires_action / processing | `PROCESSING` | không |
| succeeded (webhook) | `SUCCEEDED` | có (succeeded) |
| payment_failed (webhook) | `FAILED` | có (failed) |
| canceled (webhook) | `CANCELED` | có (failed) |

## Resilience (giới hạn Stripe ~100–200 req/s)
- `@RateLimiter("stripe")`: 100/s, chờ tối đa 2s rồi từ chối.
- `@Retry("stripe")`: backoff lũy thừa (max 4), **chỉ** lỗi tạm thời (429/timeout/5xx
  qua `StripeTransientException`); 4xx nghiệp vụ (thẻ từ chối) **không** retry → fallback FAILED.

## Idempotency (nhiều tầng)
1. `order_id` UNIQUE ⇒ 1 payment/đơn.
2. `Idempotency-Key=order:<orderId>` ⇒ Stripe không tạo 2 intent.
3. `processed_events` ⇒ webhook trùng/sai thứ tự bị bỏ qua.

## Config
```properties
server.port=8086
stripe.api-key=${STRIPE_API_KEY:sk_test_...}      # Secret, KHÔNG commit
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_...}
# (đã bỏ payment.gateway)
```
Dev webhook: `stripe listen --forward-to localhost:8086/webhooks/stripe`.
