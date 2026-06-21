# Saga — luồng mua vé (design)

> **Trạng thái:** THIẾT KẾ. Luồng mua xuyên Order/Inventory/Payment + bù trừ. Ràng buộc gốc:
> [`/CLAUDE.md`](../CLAUDE.md). Liên quan: [`inventory-no-oversell.md`](./inventory-no-oversell.md),
> [`payment-stripe-flow.md`](./payment-stripe-flow.md), [`impl/01-payment-real-stripe.md`](./impl/01-payment-real-stripe.md),
> [`outbox-debezium.md`](./outbox-debezium.md), [`services/04-order.md`](./services/04-order.md).

---

## 1. Vì sao Saga (orchestration)

Không có ACID transaction xuyên service (database-per-service). Một lượt mua chạm 3 service
sở hữu 3 DB riêng: **Order** (đơn), **Inventory** (tồn), **Payment** (tiền). Không thể
"commit cả ba" nguyên tử → dùng **Saga**: chuỗi bước cục bộ, mỗi bước có **bước bù trừ
(compensating transaction)** để hoàn tác khi bước sau thất bại.

Chọn **orchestration** (một nơi điều phối) thay vì choreography (mỗi service tự phản ứng
event) vì luồng tiền cần **trình tự rõ ràng + dễ truy vết + dễ bù trừ**. **Order là
orchestrator**, sở hữu trạng thái saga.

## 2. Happy path

```
Client ──POST /api/order──▶ ORDER (orchestrator)
                              │
            (1) tạo Order = PENDING (DB order) + lưu saga state
                              │
            (2) POST inventory /internal/holds  ───▶ INVENTORY: giữ chỗ (Redis), trả holdId
                              │  ◀── 200 held
            (3) POST payment /internal/payment-intents ─▶ PAYMENT: tạo PaymentIntent (Stripe)
                              │  ◀── 200 { clientSecret, PROCESSING } → client xác nhận, CHỜ webhook
            (4) confirm (sau webhook succeeded): Order = PAID
                + Inventory commit SOLD (Redis→Postgres)
                + outbox.fire(OrderCompleted)   ───▶ Kafka order.events
                              │                         ├─▶ TICKET: phát vé (QR ký số)
                              ▼                         └─▶ NOTIFICATION: gửi vé qua mail
                          200 Created (orderId)
```

- Bước (2)(3) là **gọi đồng bộ** service↔service qua `/internal/**` (xem
  [`API-CONVENTIONS.md`](./API-CONVENTIONS.md)).
- Bước (4) phát **event async** qua **outbox** (xem [`outbox-debezium.md`](./outbox-debezium.md))
  để Ticket/Notification xử lý — KHÔNG gọi đồng bộ (giảm thời gian giữ chỗ, tách rời).

### 2.1 ĐỒNG BỘ (thẻ) vs BẤT ĐỒNG BỘ (Konbini/Furikomi)

Hệ thống hỗ trợ đa phương thức → bước (3)(4) **rẽ hai nhánh**:

- **Thẻ (nhanh):** bước (3) tạo PaymentIntent, trả `clientSecret`; client xác nhận
  bằng **Payment Element** (±3DS). Sau khi **webhook `payment_intent.succeeded`** xác
  nhận → chạy bước (4) COMMIT + phát vé. KHÔNG fulfill ngay tại bước (3).
- **Konbini/Furikomi (bất đồng bộ):** bước (3) chỉ tạo PaymentIntent ở trạng thái
  `processing` và trả mã/hướng dẫn cho khách. Đơn chuyển **`AWAITING_PAYMENT`**, **giữ
  chỗ vẫn còn** (TTL = hạn thanh toán, [`inventory §3.1`](./inventory-no-oversell.md)).
  **CHỈ chạy bước (4)** khi nhận **`checkout.session.async_payment_succeeded` /
  `payment_intent.succeeded`** (có thể sau vài giờ→ngày). Nếu hết hạn/không trả →
  `async_payment_failed` → CANCELLED + nhả chỗ + restock + báo khách.

> 🔑 **Webhook là nguồn sự thật cho CẢ hai nhánh** — không bao giờ phát vé chỉ vì đã *tạo*
> PaymentIntent (payment_issue.md 2.4/2.6/2.7). Thẻ chỉ nhanh hơn, bản chất xác nhận giống nhau.

## 3. State machine của Order

```
                         ┌─(thẻ: succeeded ngay qua webhook)──────────────┐
PENDING ──giữ chỗ OK──┤                                                  ▼
   │   ──tạo PI──▶ AWAITING_PAYMENT ──(async_payment_succeeded)──▶ PAID ──▶ COMPLETED (đã phát vé)
   │                      │
   ├─(giữ chỗ thất bại / hết vé)──────────────▶ REJECTED
   ├─(async hết hạn / payment_failed)─────────▶ CANCELLED  → nhả chỗ + restock
   └─(huỷ / PI canceled)──────────────────────▶ CANCELLED  → nhả chỗ; refund nếu đã thu
```
- **`AWAITING_PAYMENT`**: đã tạo PaymentIntent, chờ tiền — đặc biệt quan trọng với async
  (đơn ở đây **vài giờ→ngày** là bình thường, đừng để job dọn `pending` giết oan).
- **`payment_failed` KHÔNG phải trạng thái cuối** (payment_issue.md 2.14): với thẻ, khách có
  thể thử lại trên cùng PI. Khi saga **bỏ cuộc** (hết hạn/khách huỷ) phải **CANCEL
  PaymentIntent** rồi mới nhả chỗ — tránh khách trả tiền sau đó thành "tiền mồ côi".

Order lưu **trạng thái saga** (đang ở bước nào, holdId, paymentIntentId, phương thức) để
**resume** sau restart và biết phải bù trừ bước nào.

## 4. Bù trừ (compensating transactions)

| Thất bại tại | Đã làm được | Bù trừ |
|--------------|-------------|--------|
| Giữ chỗ (2) | chưa gì | Order → REJECTED (không cần bù) |
| Thu tiền (3) | đã giữ chỗ | **nhả chỗ** (Inventory `DELETE /internal/holds/{id}`) → Order PAYMENT_FAILED |
| Xác nhận (4) | đã giữ chỗ + đã thu tiền | **refund** (Payment) + **nhả chỗ** → Order CANCELLED |

Bù trừ phải **idempotent** và nên chạy bền (retry) — đẩy vào hàng đợi/scheduler nếu cần,
không để treo trên request của client.

## 5. Idempotency xuyên service (BẮT BUỘC)

Mạng có thể timeout rồi retry → mỗi lời gọi service phải **an toàn khi lặp**:
- Order sinh **một `orderId` (UUID)** sớm; dùng làm **idempotency key** cho mọi lời gọi
  xuống Inventory/Payment.
- Inventory: "giữ chỗ cho orderId này" — gọi lại trả về **cùng holdId**, không giữ thêm.
- Payment: dùng **Stripe idempotency key** = orderId → Stripe không thu tiền hai lần.
- Ticket consumer: "đã phát vé cho orderId chưa?" trước khi phát (chống trùng do
  at-least-once của Kafka).

## 6. Ranh giới & quy ước

- **Client** chỉ nói chuyện với `/api/order/**` (qua gateway). Không gọi thẳng Inventory/Payment.
- **Order ↔ Inventory/Payment**: API nội bộ `/internal/**` (gọi qua DNS, không qua gateway;
  rào bằng NetworkPolicy — xem [`deployment-k8s.md`](./deployment-k8s.md)).
- **Order → Ticket/Notification**: event `OrderCompleted` qua outbox+Kafka (`order.events`).
- Order sở hữu **đơn + saga state**, dùng **outbox** để phát event (không publish Kafka trực tiếp).

## 7. Cạm bẫy
1. **Đừng giữ chỗ vô hạn.** Hold có **TTL** (Inventory) — nếu saga chết giữa chừng, chỗ tự
   nhả, không khoá tồn kho vĩnh viễn.
2. **Thu tiền là bước khó hoàn tác nhất** → đặt SAU khi đã giữ chỗ chắc chắn; refund là bù trừ.
3. **Xác nhận SOLD chỉ sau khi tiền chắc chắn** (hoặc qua webhook Stripe) — tránh phát vé
   rồi mới biết thanh toán hỏng.
4. **Saga phải resume được** sau restart Order (đọc saga state từ DB, tiếp tục/bù trừ).
5. Mọi bước **idempotent** (xem §5).

## 8. Thứ tự triển khai
Lát cắt **thẻ** trước để tiền chạy end-to-end (xem
[`impl/01-payment-real-stripe.md`](./impl/01-payment-real-stripe.md)); rồi **async
Konbini/Furikomi** và **ghế ngồi** dùng chung khung hold/saga/webhook đã có.
