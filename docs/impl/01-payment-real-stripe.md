# Part 1 — Thanh toán Stripe THẬT (bỏ mock) · spec triển khai

> **Mục đích:** tài liệu căn cứ để code lát cắt thanh toán thật. Đủ chi tiết để
> implement theo từng file. Bỏ hẳn `MockPaymentGateway`. Tiền chốt qua **webhook**.
> Liên quan: [`../services/05-payment.md`](../services/05-payment.md),
> [`payment-stripe-flow.md`](../payment-stripe-flow.md) (đa phương thức + cạm bẫy),
> [`saga-purchase-flow.md`](../saga-purchase-flow.md).

## 0. Phạm vi
- **Trong phạm vi (cùng khung create-intent + webhook):**
  - **Thẻ (card)** qua Payment Element — làm/chạy **trước** để có lát cắt e2e.
  - **Konbini / Furikomi (async)** — DÙNG CHUNG cơ chế, chỉ khác **event xác nhận**
    (`checkout.session.async_payment_*`) và **TTL hold dài** (xem
    [`inventory-no-oversell.md §3.1`](../inventory-no-oversell.md),
    [`saga-purchase-flow.md §2.1`](../saga-purchase-flow.md)). Đã gộp vào §3.3/§4.
- **NGOÀI phần này (part sau):** refund/hoàn tiền (bù trừ saga), ghế ngồi, 3DS UX
  nâng cao. (3DS `requires_action` vẫn chạy đúng vì đã chờ webhook.)

## 1. Quyết định kiến trúc
1. **Một nguồn sự thật cho "đã trả tiền": webhook Stripe.** Lời gọi tạo intent
   KHÔNG còn phát `PaymentSettled`. Chỉ `payment_intent.succeeded|payment_failed|
   canceled` mới phát event cho saga.
2. **Bỏ mock.** Xóa `MockPaymentGateway` + property `payment.gateway`. Chỉ còn
   `StripePaymentGateway` (luôn bật).
3. **Order saga không đổi nhánh async** — `onPaymentSettled` giữ nguyên logic
   (succeeded → commit + PAID + OrderCompleted; failed → release + PAYMENT_FAILED).
   Chỉ thay bước *đồng bộ* `place()`: thay vì "charge", nay "tạo intent + trả
   clientSecret".

## 2. Thay đổi Database

### 2.1 Payment — `V4__payment_intent.sql`
```sql
-- Mở rộng trạng thái cho luồng bất đồng bộ + lưu tham chiếu PaymentIntent.
ALTER TABLE payments ADD COLUMN stripe_pi_id VARCHAR(255);
CREATE INDEX ix_payments_pi ON payments (stripe_pi_id);
-- status nay nhận thêm: PROCESSING, CANCELED (cột vẫn VARCHAR(32), không cần đổi DDL).
```
Enum `PaymentStatus` (Java) → `PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELED`.
- `PENDING`: vừa tạo bản ghi, chưa có intent.
- `PROCESSING`: đã tạo PaymentIntent, chờ client confirm + webhook.
- `SUCCEEDED|FAILED|CANCELED`: chốt từ webhook.

> `client_secret` **KHÔNG** lưu DB (bí mật ngắn hạn). Chỉ trả về cho client 1 lần.

### 2.2 Order — `V3__order_stripe_pi.sql`
```sql
ALTER TABLE orders ADD COLUMN stripe_pi_id VARCHAR(255);  -- tiện tra cứu/đối soát
```
`OrderStatus` không đổi. (`AWAITING_PAYMENT` = đã tạo intent, chờ webhook.)

## 3. Hợp đồng API

### 3.1 Payment — `POST /internal/payment-intents`  *(thay `POST /internal/charges`)*
**Request**
```json
{ "orderId": "uuid", "amountMinor": 100000, "currency": "VND" }
```
**Response 200**
```json
{ "paymentId":"uuid", "stripePiId":"pi_...", "clientSecret":"pi_..._secret_...",
  "status":"PROCESSING" }
```
- Idempotent: gọi lại cùng `orderId` → trả intent cũ (cùng `clientSecret` nếu Stripe
  cho lấy lại; nếu không, trả bản ghi cũ + client lấy lại qua GET intent — xem §6.4).
- Stripe call: `PaymentIntent.create` với
  `automatic_payment_methods.enabled=true`, `metadata.order_id=<orderId>`,
  `Idempotency-Key = "order:<orderId>"`.

### 3.2 Payment — `GET /internal/payments/by-order/{orderId}` *(giữ nguyên)*
Trả `{paymentId, orderId, status, stripePiId, amountMinor, currency}` — cho
`OrderReconciliationJob` hỏi lại khi đơn kẹt.

### 3.3 Payment — `POST /webhooks/stripe` *(giữ controller, mở rộng service)*
Giữ verify chữ ký + idempotent (`processed_events`) + đối chiếu amount/currency.
Map từ `metadata.order_id`. Fire `PaymentSettled` qua outbox. Xử lý:
- **Thẻ:** `payment_intent.succeeded`→SUCCEEDED · `payment_intent.payment_failed`
  →FAILED · `payment_intent.canceled`→CANCELED.
- **Async (Konbini/Furikomi):** `checkout.session.async_payment_succeeded`
  →SUCCEEDED · `checkout.session.async_payment_failed`→FAILED ·
  `payment_intent.processing`→PROCESSING (chỉ cập nhật, KHÔNG settle).

### 3.4 Order — `POST /api/order`
Response thêm `clientSecret`:
```json
{ "id":"uuid","status":"AWAITING_PAYMENT","eventId":"...","ticketTypeId":"...",
  "quantity":1,"amountMinor":100000,"currency":"VND","paymentId":"uuid",
  "clientSecret":"pi_..._secret_...","failureReason":null }
```
`GET /api/order/{id}` giữ nguyên (FE poll). `clientSecret` chỉ có ở response của
POST (không trả lại ở GET — tránh rò rỉ; nếu cần resume, FE giữ trong state/session).

## 4. Map trạng thái PaymentIntent → PaymentStatus
| PaymentIntent.status (Stripe) | Nguồn | PaymentStatus | Hành động |
|---|---|---|---|
| `requires_payment_method` / `requires_confirmation` / `requires_action` / `processing` | create / poll | `PROCESSING` | chờ, KHÔNG phát settled |
| `succeeded` | **webhook** `payment_intent.succeeded` | `SUCCEEDED` | fire PaymentSettled(succeeded) |
| (card declined) | **webhook** `payment_intent.payment_failed` | `FAILED` | fire PaymentSettled(failed) |
| `canceled` | **webhook** `payment_intent.canceled` | `CANCELED` | fire PaymentSettled(failed) |
| Konbini trả xong | **webhook** `checkout.session.async_payment_succeeded` | `SUCCEEDED` | fire PaymentSettled(succeeded) |
| Konbini hết hạn/thất bại | **webhook** `checkout.session.async_payment_failed` | `FAILED` | fire PaymentSettled(failed) |

> Lúc `create` đừng bao giờ suy ra `SUCCEEDED`/`FAILED` từ status trả về ngay — luôn
> để `PROCESSING` và đợi webhook (sửa bug hiện tại map non-succeeded → FAILED).
> Đơn async để `AWAITING_PAYMENT` **vài giờ→ngày là bình thường** — TTL hold đặt theo
> phương thức (inventory §3.1), reconciliation không quét nhầm.

## 5. Sửa code theo file

### Payment service
| File | Thay đổi |
|---|---|
| `entities/PaymentStatus.java` | thêm `PROCESSING`, `CANCELED` |
| `entities/Payment.java` | thêm field `stripePiId`; method `startIntent(piId)` set status=PROCESSING + piId |
| `gateway/PaymentGateway.java` | đổi `charge(...)` → `createIntent(orderId, amountMinor, currency, idemKey)` trả record `IntentResult(piId, clientSecret, Status status)` với `Status{PROCESSING, FAILED}` |
| `gateway/StripePaymentGateway.java` | `PaymentIntent.create` với `automatic_payment_methods`; lấy `pi.getClientSecret()`; status lỗi tạo intent (4xx) → FAILED, transient → `@Retry` |
| `gateway/MockPaymentGateway.java` | **XÓA** |
| `services/impl/PaymentServiceImpl.java` | `charge()` → `createIntent()`: tạo Payment(PENDING) → gọi gateway → `payment.startIntent(piId)` (PROCESSING) → **KHÔNG fire PaymentSettled** → trả clientSecret. Giữ idempotency theo `order_id` UNIQUE |
| `dto/ChargeRequest/ChargeResponse` | đổi tên/thêm `CreateIntentRequest`, `IntentResponse{paymentId,stripePiId,clientSecret,status}` |
| `controller/InternalPaymentController.java` | `POST /internal/charges` → `POST /internal/payment-intents` |
| `webhook/StripeWebhookService.java` | thêm case `payment_intent.canceled` → `applyStatus(FAILED/CANCELED)` |
| `resources/application.properties` | **xóa** `payment.gateway=...`; giữ `stripe.api-key`, `stripe.webhook-secret`, rate limiter/retry |

### Order service
| File | Thay đổi |
|---|---|
| `client/PaymentClient.java` | `charge(...)` → `createIntent(...)` gọi `POST /internal/payment-intents`, parse `clientSecret` |
| `entities/Order.java` | thêm `stripePiId`; `awaitPayment(holdId, paymentId, piId)` |
| `services/impl/OrderServiceImpl.java` | `place()`: sau hold → `payment.createIntent` → set paymentId/piId + AWAITING_PAYMENT + commit → trả clientSecret. `onPaymentSettled` **giữ nguyên** |
| `dto/OrderResponse.java` | thêm `clientSecret` (chỉ map khi vừa tạo) |

### Web (`web/`)
| Việc | Chi tiết |
|---|---|
| Cài lib | `@stripe/stripe-js`, `@stripe/react-stripe-js` |
| Env | `VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...` |
| `EventDetailPage.tsx` | POST order → nhận `clientSecret` → render `<Elements stripe options={{clientSecret}}>` + `<PaymentElement/>` → `stripe.confirmPayment(...)` → **poll** `GET /api/order/{id}` tới PAID/PAYMENT_FAILED; hiện "⏳ Đang xử lý" lúc AWAITING_PAYMENT (sửa luôn bug nhãn "thất bại") |

## 6. Luồng chi tiết & idempotency
```
1. FE POST /api/order {eventId,ticketTypeId,quantity}
2. Order: tạo order PENDING → Inventory POST /internal/holds → holdId
3. Order → Payment POST /internal/payment-intents {orderId,amountMinor,currency}
   Payment: insert payments(order_id UNIQUE, PENDING)
            Stripe PaymentIntent.create (Idempotency-Key=order:<id>, metadata.order_id)
            payments.status=PROCESSING, stripe_pi_id=pi_...
            return {paymentId, stripePiId, clientSecret, PROCESSING}   ← KHÔNG fire event
4. Order: order.awaitPayment(holdId,paymentId,piId) → AWAITING_PAYMENT (COMMIT)
   trả 201 {order, clientSecret}
5. FE: confirmPayment(clientSecret, PaymentElement)  (3DS nếu cần)
6. Stripe → POST /webhooks/stripe (payment_intent.succeeded)
   Payment: verify sig+ts → processed_events idempotent → đối chiếu amount/currency
            payments.settle(SUCCEEDED, piId) → outbox PaymentSettled(succeeded)
7. Order onPaymentSettled(succeeded): isAwaitingPayment? → Inventory commit(holdId)
            order.markPaid(paymentId) → outbox OrderCompleted
8. Ticket consume OrderCompleted → phát vé + QR;  Notification → email
9. FE poll GET /api/order/{id} → PAID
```
**Bất biến idempotency:**
- 6.1 `order_id` UNIQUE ⇒ 1 payment/đơn. Gọi tạo intent lại → trả bản ghi cũ.
- 6.2 `Idempotency-Key=order:<orderId>` ⇒ Stripe không tạo 2 PaymentIntent.
- 6.3 `processed_events(event_id)` ⇒ webhook trùng/sai thứ tự → bỏ qua.
- 6.4 Webhook tới TRƯỚC khi Order kịp COMMIT AWAITING_PAYMENT: `onPaymentSettled`
  thấy order chưa `AWAITING_PAYMENT` → bỏ qua; `OrderReconciliationJob` hỏi lại
  Payment sau đó (GET by-order) → reconcile. (At-least-once, không mất.)

## 7. Cấu hình & webhook local
```properties
# payment/application.properties (bỏ payment.gateway)
stripe.api-key=${STRIPE_API_KEY:sk_test_...}
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_...}
```
- Nạp khoá test qua env/Secret — **KHÔNG commit**.
- Dev nhận webhook: `stripe listen --forward-to localhost:8086/webhooks/stripe`
  → copy `whsec_...` CLI in ra vào `STRIPE_WEBHOOK_SECRET`.
- Test thẻ: `4242 4242 4242 4242` (succeeded), `4000 0000 0000 9995` (declined),
  `4000 0027 6000 3184` (3DS required).

## 8. Edge cases bắt buộc xử lý
1. **Tạo intent lỗi nghiệp vụ (4xx)** → IntentResult FAILED → Order `PAYMENT_FAILED`
   + nhả chỗ ngay (không có gì để chờ webhook).
2. **Tạo intent lỗi tạm thời (429/5xx/timeout)** → `@Retry` backoff; hết retry →
   FAILED + nhả chỗ; reconciliation phân xử "intent mồ côi".
3. **Đối chiếu amount/currency lệch ở webhook** → log lỗi, KHÔNG áp dụng (chống thao túng).
4. **Client bỏ ngang (không confirm)** → đơn kẹt AWAITING_PAYMENT → reconciliation/TTL
   hold hết hạn → (part sau) cancel intent + nhả chỗ.
5. **3DS `requires_action`** → FE `confirmPayment` tự mở 3DS; kết quả vẫn về qua webhook.

## 9. Acceptance criteria (định nghĩa "xong")
- [ ] Không còn `MockPaymentGateway`/`payment.gateway` trong repo.
- [ ] Đặt đơn → có giao dịch **PaymentIntent thật** trên Stripe Dashboard (test mode).
- [ ] Thẻ `4242...` → đơn về `PAID`, có vé + QR; thẻ declined → `PAYMENT_FAILED`,
      nhả chỗ, KHÔNG vé.
- [ ] Tắt `stripe listen` (không webhook) → đơn ở `AWAITING_PAYMENT`; bật lại →
      reconcile về trạng thái cuối (không mất tiền/không kẹt vĩnh viễn).
- [ ] Gửi lại cùng webhook event → không phát vé/đổi trạng thái lần hai.
- [ ] FE: lúc chờ hiện "Đang xử lý", chỉ báo "thất bại" khi `PAYMENT_FAILED` thật.
- [ ] `smoke-test.sh` vẫn xanh (các assertion biên/security không phụ thuộc mock).
