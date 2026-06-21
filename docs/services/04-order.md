# 4. Order service (`order/`) — Saga orchestrator

> Sâu hơn: [`saga-purchase-flow.md`](../flows/saga-purchase-flow.md) — happy path, bù trừ, idempotency xuyên service.

## Trách nhiệm
**Trái tim điều phối.** Tạo đơn → giữ chỗ (Inventory) → tạo PaymentIntent (Payment)
→ chờ kết quả → khi succeeded: commit SOLD + đơn PAID + phát `OrderCompleted`; khi
failed: **bù trừ** (nhả chỗ) + PAYMENT_FAILED. Sở hữu đơn + trạng thái saga + outbox.

## Database `order`

### orders
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL, `ix_orders_user` (IDOR: chỉ chủ đơn xem được) |
| email | VARCHAR(320) | NULL — claim JWT, để phát OrderCompleted ở bước async |
| event_id | UUID | NOT NULL |
| ticket_type_id | UUID | NOT NULL |
| quantity | INTEGER | NOT NULL CHECK ≥ 1 |
| seat_ids | UUID[] | NULL — nếu SEATED |
| amount_minor | BIGINT | NOT NULL CHECK ≥ 0 (Order tự tính từ giá Catalog × qty) |
| currency | VARCHAR(3) | NOT NULL |
| status | VARCHAR(32) | NOT NULL — state machine (dưới) |
| hold_id | UUID | NULL — trạng thái saga: id giữ chỗ Inventory |
| payment_id | UUID | NULL — id payment ở Payment |
| stripe_pi_id | VARCHAR(255) | NULL — tham chiếu PaymentIntent |
| failure_reason | VARCHAR(500) | NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

### outbox  (khuôn chuẩn — phát `OrderCompleted`)

## State machine (`OrderStatus`)
```
PENDING ─hold OK + intent tạo─► AWAITING_PAYMENT ─webhook succeeded─► PAID
   │                                   │
   └─hold fail─► REJECTED              └─webhook failed/intent lỗi─► PAYMENT_FAILED
```
- `PENDING`: vừa tạo, chưa giữ chỗ.
- `AWAITING_PAYMENT`: đã hold + đã tạo PaymentIntent, **chờ webhook** (bình thường kéo dài).
- `PAID`: đã commit SOLD, đã phát OrderCompleted.
- `REJECTED`: không giữ được chỗ (hết vé).
- `PAYMENT_FAILED`: thu tiền hỏng → đã nhả chỗ.

## API
| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/api/order` | JWT | GA `{eventId,ticketTypeId,quantity}` · SEATED `{eventId,ticketTypeId,seatIds[]}` | 201 `OrderResponse` + **`clientSecret`** · 400 body sai · 409 hết chỗ |
| GET | `/api/order/{id}` | JWT (chủ đơn) | – | 200 `OrderResponse` (FE **poll** tới trạng thái cuối) · 404 (kể cả đơn người khác — IDOR) |

`OrderResponse`: `{id,status,eventId,ticketTypeId,quantity,amountMinor,currency,
paymentId,clientSecret?,failureReason?}`. `clientSecret` **chỉ** ở response POST.

## Luồng `place()` (đồng bộ) — Stripe thật
```
1. validate body + lấy giá từ Catalog → amount_minor = price × qty
2. tạo order PENDING (commit nhẹ) 
3. Inventory POST /internal/holds → holdId   (fail → REJECTED)
4. Payment POST /internal/payment-intents {orderId,amount,currency}
        → {paymentId, stripePiId, clientSecret, PROCESSING}
5. order.awaitPayment(holdId,paymentId,piId) → AWAITING_PAYMENT (COMMIT)
6. trả 201 {order, clientSecret}      ← saga DỪNG, tiếp tục async
```

## Luồng async `onPaymentSettled` (consume `payment.events`)
```
nếu order KHÔNG ở AWAITING_PAYMENT → bỏ qua (idempotent, at-least-once)
succeeded → Inventory commit(holdId) → order.markPaid(paymentId)
           → outbox OrderCompleted
failed/canceled → Inventory delete hold (nhả) → order.failPayment(reason)
```

## Job nền
`OrderReconciliationJob`: với đơn kẹt `AWAITING_PAYMENT` quá `cutoff`, gọi Payment
`GET /internal/payments/by-order/{id}` (nguồn sự thật) để phân xử — chống mất webhook.

## Invariant
- Order **tự tính tiền** từ Catalog (không tin client gửi amount).
- Mọi bước async **idempotent** theo trạng thái đơn (chỉ tác động khi đang chờ).
- `OrderCompleted` chỉ phát đúng một lần, qua **outbox** (không mất khi crash).

## Config
`server.port=8084`, DB `order`. Client: `CatalogClient`, `InventoryClient`,
`PaymentClient` (Resilience4j retry/circuit breaker). Consumer `payment.events`.
