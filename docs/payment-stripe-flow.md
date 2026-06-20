# Payment — tích hợp Stripe (design)

> **Trạng thái:** THIẾT KẾ định hướng. `payment` hiện là skeleton. Doc này mô tả LUỒNG khi
> build; các **cạm bẫy chi tiết** đã có ở [`payment_issue.md`](./payment_issue.md) (đọc kèm).
> Ràng buộc gốc: [`/CLAUDE.md`](../CLAUDE.md). Liên quan: [`saga-purchase-flow.md`](./saga-purchase-flow.md),
> [`resilience-flash-sale.md`](./resilience-flash-sale.md).

---

## 1. Vai trò
Payment là **cổng DUY NHẤT** gọi ra Stripe (một tài khoản, dùng **Payment Intents**). Sở
hữu: bản ghi thanh toán, idempotency key, tham chiếu Stripe. Phạm vi hiện tại: **chưa
Connect, chưa tách Ledger** (chia tiền nhiều bên để sau).

## 2. Hai chiều giao tiếp với Stripe

```
        Order ──POST /internal/charges──▶ PAYMENT ──(rate limiter + retry)──▶ Stripe API
                                            │                                  (PaymentIntent)
                                            ▼
                                       DB payment: lưu paymentId, status, idempotencyKey

   Stripe ──webhook──▶ nginx/Ingress (DMZ, BỎ QUA gateway) ──▶ PAYMENT /webhooks/stripe
                                            │ verify chữ ký Stripe (không JWT)
                                            ▼ đẩy vào Kafka rồi mới xử lý (idempotent)
                                       cập nhật trạng thái thanh toán → báo Order
```

- **Chiều ra (gọi Stripe):** đồng bộ trong saga, có **rate limiter + retry backoff**.
- **Chiều vào (webhook):** Stripe gọi ngược để báo kết quả thật (succeeded/failed) — nguồn
  xác nhận đáng tin nhất.

## 3. Giới hạn Stripe phải tôn trọng
- ~**100–200 request/giây mỗi tài khoản**; vượt = HTTP **429**.
- Payment **bắt buộc** có **rate limiter** (không vượt ngưỡng) + **retry exponential backoff**
  (Resilience4j) để xử lý 429 sạch sẽ. Xem [`resilience-flash-sale.md`](./resilience-flash-sale.md).
- Đây cũng là lý do **Waiting Room** giới hạn nhịp vào — admission phải biết cả ngưỡng Stripe.

## 4. Idempotency (bắt buộc, nhiều tầng)
- **Stripe idempotency key = `orderId`**: gọi tạo PaymentIntent lại (do retry/timeout) →
  Stripe trả về CÙNG intent, **không thu tiền hai lần**.
- **Webhook idempotent:** mỗi event Stripe có id; xử lý lại event đã thấy → bỏ qua (đã đẩy
  Kafka/đã cập nhật). Webhook có thể tới **nhiều lần** và **không đúng thứ tự**.
- Bản ghi payment khoá theo `orderId`/`paymentIntentId` để chống tạo trùng.

## 5. Webhook — quy trình an toàn
1. **Verify chữ ký Stripe** (dùng `stripe.webhook-secret`) — chống giả mạo. KHÔNG phải JWT.
2. **Idempotent**: kiểm event id đã xử lý chưa.
3. **Đẩy vào Kafka rồi MỚI xử lý** (không xử lý nặng ngay trong request webhook) → trả 200
   nhanh cho Stripe, xử lý async, chịu lỗi tốt.
4. Endpoint đặt ở `/webhooks/stripe`, vào qua **reverse proxy DMZ** (nginx dev / Ingress
   prod), **bỏ qua apigateway** (xem [`API-CONVENTIONS.md`](./API-CONVENTIONS.md) §5 và
   [`deployment-k8s.md`](./deployment-k8s.md)).

## 6. Trạng thái PaymentIntent (rút gọn)
```
requires_payment_method → requires_confirmation → processing → succeeded
                                                            └─▶ requires_action (3DS)
                                                            └─▶ canceled / failed
```
- **Chỉ coi là PAID khi `succeeded`** (ưu tiên xác nhận qua webhook) → mới cho Inventory
  COMMIT SOLD và phát vé (xem Saga §7).
- `requires_action` (3D Secure) → cần client xác thực thêm; saga chờ webhook.

## 7. Đa phương thức — thẻ (sync) + Konbini/Furikomi (async)

Hệ thống bật **cả thẻ lẫn Konbini/Furikomi**. Mọi logic phụ thuộc thời điểm tiền về phải
**tham số hoá theo phương thức** (KHÔNG dùng một bộ giá trị chung — payment_issue.md 7.5).

| Phương thức | Tiền về | Sự kiện xác nhận | TTL giữ chỗ |
|-------------|---------|------------------|-------------|
| Thẻ | gần như ngay (±3DS) | `payment_intent.succeeded` | vài phút |
| **Konbini** | sau vài giờ→ngày (trả tại cửa hàng) | `checkout.session.async_payment_succeeded` | vài giờ→ngày |
| **Furikomi** | sau khi khách chuyển khoản | `payment_intent.succeeded` (sau khi đủ tiền) | theo hạn |

**Bất đồng bộ — quy tắc bắt buộc:**
- Đơn để **`AWAITING_PAYMENT` lâu là bình thường**; không fulfill tới khi nhận event
  `async_payment_succeeded` (saga §2.1). Xử lý cả nhánh `async_payment_failed`/hết hạn →
  CANCELLED + nhả chỗ + restock + báo khách (payment_issue.md 7.1, 7.2).
- **Konbini hết hạn:** đặt hạn hợp lý + (tuỳ chọn) email nhắc trước hạn để giảm rớt đơn.
- **Konbini refund KHÔNG về phương thức gốc** (khách trả tiền mặt): cần khách cung cấp
  thông tin ngân hàng → trạng thái `refund_pending_customer_info`, KHÔNG set `refunded`
  ngay khi gọi API (payment_issue.md 7.3).
- **Furikomi trả thiếu/thừa:** phần lệch nằm ở **customer balance** → cần chính sách rõ
  (chờ chuyển nốt / hoàn / giữ credit) + đưa "tiền lửng lơ" vào đối soát (payment_issue.md 7.4).

**Đơn vị tiền (mọi phương thức):** dùng `amount` theo **minor units + currency** từ Catalog;
**JPY là zero-decimal** (không ×100). Một hàm chuyển đổi tập trung, có test riêng JPY
(payment_issue.md 6.1).

## 8. Cấu hình (đã có khung trong `payment/application.properties`)
```properties
stripe.api-key=${STRIPE_API_KEY:sk_test_dummy}          # khoá bí mật — nạp từ K8s Secret
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_dummy}
```
KHÔNG commit khoá thật. Dev dùng khoá test giả.

## 9. Cạm bẫy (tóm tắt — chi tiết ở payment_issue.md)
1. **Không thu tiền hai lần** → idempotency key.
2. **Webhook đến trễ / trùng / sai thứ tự** → idempotent + dựa trạng thái cuối, không giả định.
3. **Vượt 429** → rate limiter + backoff; đừng retry dồn dập (làm tệ hơn).
4. **Tiền là bước khó hoàn tác** → refund là bù trừ saga, phải bền + idempotent.
5. **Mọi bước idempotent xuyên service** (Order ↔ Payment ↔ Stripe).
