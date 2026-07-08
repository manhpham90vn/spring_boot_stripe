# Saga — luồng mua vé (design)

> **Trạng thái:** THIẾT KẾ, đã triển khai lát cắt thẻ. Luồng mua xuyên Order/Inventory/Payment
> + bù trừ. Ràng buộc gốc: [`/CLAUDE.md`](../../CLAUDE.md). Liên quan:
> [`inventory-no-oversell.md`](inventory-no-oversell.md), [`payment-stripe-flow.md`](payment-stripe-flow.md),
> [`impl/01-payment-real-stripe.md`](../impl/01-payment-real-stripe.md),
> [`outbox-debezium.md`](../standards/outbox-debezium.md), [`services/04-order.md`](../services/04-order.md).

---

## 1. Vì sao Saga (orchestration)

Không có ACID transaction xuyên service (database-per-service). Một lượt mua chạm 3 service
sở hữu 3 DB riêng: **Order** (đơn), **Inventory** (tồn), **Payment** (tiền). Không thể
"commit cả ba" nguyên tử → dùng **Saga**: chuỗi bước cục bộ, mỗi bước có **bước bù trừ
(compensating transaction)** để hoàn tác khi bước sau thất bại.

Chọn **orchestration** (một nơi điều phối) thay vì choreography (mỗi service tự phản ứng
event) vì luồng tiền cần **trình tự rõ ràng + dễ truy vết + dễ bù trừ**. **Order là
orchestrator**, sở hữu trạng thái saga.

## 2. Happy path — HAI PHA

Saga chạy **hai pha tách rời**. Pha A đồng bộ trong request của client; pha B bất đồng bộ,
kích hoạt bởi webhook Stripe (có thể sau vài giây với thẻ, **vài giờ→ngày** với Konbini).

### Pha A — request `POST /api/order` (đồng bộ, `OrderServiceImpl#place`)

```
Client ──POST /api/order──▶ ORDER (orchestrator)
                              │
    (1) GET catalog /internal/ticket-types/…  → LẤY GIÁ ở server (không tin giá client gửi)
    (2) tạo Order = PENDING (DB order, commit riêng)
    (3) POST inventory /internal/holds        → giữ chỗ Redis, trả holdId
          └─ 409 hết vé → Order = REJECTED, trả về luôn
    (4) POST payment /internal/payment-intents → tạo PaymentIntent (Stripe), trả clientSecret
          └─ lỗi → BÙ TRỪ: nhả chỗ → Order = PAYMENT_FAILED, trả về luôn
    (5) Order = AWAITING_PAYMENT (lưu holdId, paymentId, piId) + COMMIT
                              │
                              ▼
        201 { orderId, status: AWAITING_PAYMENT, clientSecret }
```

**Request kết thúc Ở ĐÂY** — client cầm `clientSecret` xác nhận thẻ bằng **Payment Element**
(±3DS). Saga **dừng** ở `AWAITING_PAYMENT` chờ kết quả từ webhook. KHÔNG bao giờ trả PAID
trong request này.

### Pha B — webhook chốt kết quả (bất đồng bộ, `OrderServiceImpl#onPaymentSettled`)

```
Stripe ──webhook──▶ PAYMENT: verify chữ ký + idempotent + đối chiếu amount
                       │  settle payment (DB) + outbox.fire(PaymentSettled)
                       ▼
                    Kafka payment.events
                       │
                       ▼
                    ORDER #onPaymentSettled (idempotent: chỉ xử lý đơn AWAITING_PAYMENT)
                       ├─ SUCCEEDED → Inventory commit SOLD (Redis→Postgres) → Order = PAID
                       │                + outbox.fire(OrderCompleted) ──▶ Kafka order.events
                       │                      ├─▶ TICKET: phát vé (QR ký số)
                       │                      └─▶ NOTIFICATION: gửi vé qua mail
                       └─ FAILED/CANCELED → bù trừ (§4): hủy PI → nhả chỗ → PAYMENT_FAILED
```

- Bước (3)(4) pha A là **gọi đồng bộ** qua `/internal/**` (xem
  [`API-CONVENTIONS.md`](../standards/API-CONVENTIONS.md)).
- Kết quả thanh toán về Order qua **Kafka `payment.events`** (Payment phát bằng outbox) —
  KHÔNG gọi đồng bộ ngược. Tương tự, Order báo Ticket/Notification qua **`order.events`**
  (xem [`outbox-debezium.md`](../standards/outbox-debezium.md)).
- **Lưới an toàn**: nếu event `payment.events` bị mất, `OrderReconciliationJob` định kỳ tìm
  đơn kẹt `AWAITING_PAYMENT` quá lâu, **hỏi lại Payment** (`GET /internal/payment-intents/by-order`)
  rồi tự đẩy saga đi tiếp qua chính `onPaymentSettled` (idempotent).

### 2.1 Thẻ vs Konbini/Furikomi — CÙNG một khung, khác thời gian chờ

Hai phương thức đi **cùng pha A/pha B** ở trên, chỉ khác nhịp:

- **Thẻ (nhanh):** client xác nhận ngay bằng Payment Element → webhook
  `payment_intent.succeeded` về sau vài giây → pha B chạy gần như liền mạch.
- **Konbini/Furikomi (chậm):** bước (4) trả mã/hướng dẫn nộp tiền; đơn nằm ở
  `AWAITING_PAYMENT` **vài giờ→ngày** (giữ chỗ TTL = hạn thanh toán,
  [`inventory §3.1`](inventory-no-oversell.md)). Webhook `payment_intent.succeeded` /
  `payment_intent.payment_failed` (hết hạn không nộp) về muộn → pha B chạy lúc đó.
  *(Chưa triển khai — cần thêm bước tích hợp riêng, xem
  [`impl/01-payment-real-stripe.md`](../impl/01-payment-real-stripe.md) §0.)*

> 🔑 **Webhook là nguồn sự thật cho CẢ hai** ([`payment_issue.md`](../payment-ref/payment_issue.md)
> 2.4/2.6/2.7) — không bao giờ phát vé chỉ vì đã *tạo* PaymentIntent. Thẻ chỉ nhanh hơn,
> bản chất xác nhận giống nhau.

## 3. State machine của Order

Khớp `OrderStatus.java` (nguồn: `order/…/entities/OrderStatus.java`):

```
PENDING ──(3) hết vé──────────────────────────▶ REJECTED                    (cuối)
   │
   ├──(4) tạo PI lỗi → nhả chỗ────────────────▶ PAYMENT_FAILED              (cuối)
   │
   └──(5) hold OK + PI OK──▶ AWAITING_PAYMENT
                                  │
                                  ├─webhook succeeded + commit SOLD OK──▶ PAID
                                  │                                        └──▶ COMPLETED *
                                  ├─webhook succeeded NHƯNG hold hết hạn
                                  │    → refund + nhả chỗ────────────────▶ CANCELLED       (cuối)
                                  └─webhook failed/canceled
                                       → hủy PI + nhả chỗ────────────────▶ PAYMENT_FAILED  (cuối)
```

- **`AWAITING_PAYMENT`**: đã giữ chỗ + đã tạo PaymentIntent, chờ tiền. Với async đơn nằm đây
  **vài giờ→ngày** là bình thường — job dọn dẹp KHÔNG được giết nhầm; chỉ
  `OrderReconciliationJob` hỏi lại Payment rồi quyết.
- **`PAYMENT_FAILED`**: thất bại **trước khi thu được tiền** — đã bù trừ (nhả chỗ). Khác với
  **`CANCELLED`**: **đã thu tiền** nhưng không chốt được SOLD — đã bù trừ (refund + nhả chỗ).
- **`COMPLETED`** (*): trạng thái **thiết kế, CHƯA có trong code** — sẽ chuyển từ PAID khi
  Ticket xác nhận đã phát vé (feedback Ticket→Order chưa wire; slice hiện tại dừng ở PAID).

### 3.1 Chính sách khi thanh toán thất bại: fail-fast + HỦY PaymentIntent

Với thẻ, `payment_intent.payment_failed` không phải chung cuộc về phía Stripe — khách *có
thể* thử lại trên cùng PI ([`payment_issue.md`](../payment-ref/payment_issue.md) 2.14). Hệ này
**chọn fail-fast** (phù hợp flash sale: nhả tồn kho sớm cho người xếp hàng sau): fail lần
đầu = đơn kết thúc. Muốn mua lại → đặt đơn mới.

Fail-fast bắt buộc **thứ tự bù trừ**: **HỦY PaymentIntent TRƯỚC, nhả chỗ SAU**. Nếu nhả chỗ
mà PI còn sống, khách retry trên Payment Element cũ có thể **trả tiền cho đơn đã bỏ** →
"tiền mồ côi" (thu tiền, không vé, không refund). Cụ thể (`onPaymentSettled`, nhánh FAILED):

1. Gọi `POST payment /internal/cancellations {orderId}` (idempotent).
2. Payment hủy PI ở Stripe. **PI đã kịp `succeeded`/`processing`** (khách xác nhận đúng lúc
   fail) → Payment trả **409** → Order **GIỮ `AWAITING_PAYMENT`**, không nhả chỗ — webhook
   `succeeded` (hoặc reconciliation) sẽ chốt đơn theo nhánh thành công.
3. Hủy OK → nhả chỗ → `PAYMENT_FAILED`.
4. Lỗi tạm thời (5xx/timeout) khi hủy → ném ra cho Kafka redeliver, thử lại (idempotent).

## 4. Bù trừ (compensating transactions)

| Thất bại tại | Đã làm được | Bù trừ | Trạng thái |
|--------------|-------------|--------|------------|
| Giữ chỗ (3) | chưa gì | không cần | REJECTED |
| Tạo PaymentIntent (4) | đã giữ chỗ | nhả chỗ (`DELETE /internal/holds/{id}`) | PAYMENT_FAILED |
| Webhook failed/canceled | đã giữ chỗ + PI đang sống | **hủy PI** (`POST /internal/cancellations`) → nhả chỗ (§3.1) | PAYMENT_FAILED |
| Commit SOLD sau succeeded (hold hết hạn) | đã giữ chỗ + **đã thu tiền** | **refund** (`POST /internal/refunds`, idempotency key `refund:order:<orderId>`) → nhả chỗ | CANCELLED |

Bù trừ phải **idempotent** và chạy bền: lỗi tạm thời (5xx/timeout) khi bù trừ → **KHÔNG**
nuốt, ném ra để Kafka redeliver thử lại; chỉ lỗi chung cuộc (404 hold, 409 không hủy được)
mới rẽ nhánh. Cả bốn hàng đã triển khai — xem `OrderServiceImpl#place/#onPaymentSettled`,
[`services/05-payment.md`](../services/05-payment.md) §Refund/§Cancel.

## 5. Idempotency xuyên service (BẮT BUỘC)

Mạng có thể timeout rồi retry, Kafka là at-least-once → mỗi bước phải **an toàn khi lặp**:
- Order sinh **một `orderId` (UUID)** sớm; dùng làm **idempotency key** cho mọi lời gọi
  xuống Inventory/Payment.
- Inventory: "giữ chỗ cho orderId này" — gọi lại trả về **cùng holdId**, không giữ thêm.
- Payment: Stripe idempotency key theo đơn (`order:<orderId>`, `refund:order:<orderId>`) →
  Stripe không tạo hai PI / không hoàn hai lần; hủy PI idempotent (đã CANCELED → trả lại).
- Order consumer (`onPaymentSettled`): chỉ tác động khi đơn còn `AWAITING_PAYMENT` — event
  trùng/đến muộn bị bỏ qua.
- Ticket consumer: "đã phát vé cho orderId chưa?" trước khi phát.

## 6. Ranh giới & quy ước

- **Client** chỉ nói chuyện với `/api/order/**` (qua gateway). Không gọi thẳng Inventory/Payment.
- **Order → Inventory/Payment**: API nội bộ `/internal/**` (DNS, không qua gateway; rào bằng
  NetworkPolicy — xem [`deployment-k8s.md`](../ops/deployment-k8s.md)).
- **Payment → Order**: event `PaymentSettled` qua outbox+Kafka (**`payment.events`**) — kết
  quả webhook KHÔNG gọi đồng bộ ngược vào Order.
- **Order → Ticket/Notification**: event `OrderCompleted` qua outbox+Kafka (**`order.events`**).
- Order sở hữu **đơn + saga state** (`status`, `holdId`, `paymentId`, `stripePiId`) để
  **resume** sau restart và biết phải bù trừ bước nào; phát event luôn qua **outbox**.

## 7. Cạm bẫy
1. **Đừng giữ chỗ vô hạn.** Hold có **TTL** (Inventory) — saga chết giữa chừng thì chỗ tự
   nhả, không khoá tồn kho vĩnh viễn.
2. **Thu tiền là bước khó hoàn tác nhất** → đặt SAU khi đã giữ chỗ chắc chắn; refund là bù trừ.
3. **Xác nhận SOLD chỉ sau webhook** — tránh phát vé rồi mới biết thanh toán hỏng.
4. **Nhả chỗ khi PI còn sống = "tiền mồ côi"** — luôn hủy PI trước (§3.1).
5. **Saga phải resume được** sau restart Order: đọc saga state từ DB + reconciliation job
   hỏi lại Payment cho đơn kẹt.
6. Mọi bước **idempotent** (§5).

## 8. Thứ tự triển khai
Lát cắt **thẻ** trước để tiền chạy end-to-end (xem
[`impl/01-payment-real-stripe.md`](../impl/01-payment-real-stripe.md)); rồi **async
Konbini/Furikomi** và **ghế ngồi** dùng chung khung hold/saga/webhook đã có.
