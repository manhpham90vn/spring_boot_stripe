# Thiết kế chi tiết theo service

Mỗi file = thiết kế chi tiết một service (trách nhiệm, database DDL, Redis,
hợp đồng API, event Kafka, invariant, config) — **căn cứ để triển khai**.

| # | Service | DB chính | File |
|---|---|---|---|
| 0 | API Gateway | – (Redis biên) | [00-apigateway.md](00-apigateway.md) |
| 1 | Auth/User | PostgreSQL `auth` | [01-auth.md](01-auth.md) |
| 2 | Catalog | PostgreSQL `catalog` | [02-catalog.md](02-catalog.md) |
| 3 | Inventory | PostgreSQL `inventory` + Redis | [03-inventory.md](03-inventory.md) |
| 4 | Order (Saga) | PostgreSQL `order` | [04-order.md](04-order.md) |
| 5 | Payment | PostgreSQL `payment` | [05-payment.md](05-payment.md) |
| 6 | Ticket | PostgreSQL `ticket` | [06-ticket.md](06-ticket.md) |
| 7 | Notification | PostgreSQL `notification` | [07-notification.md](07-notification.md) |
| 8 | Waiting Room | Redis | [08-waitingroom.md](08-waitingroom.md) |

## Quy ước xuyên suốt
- **Path/quyền:** `public/**` (không JWT) · `/**` (JWT user) · `admin/**` (ADMIN) ·
  `/internal/**` (service↔service, không lộ ở apigateway) · `/webhooks/**` (Stripe, DMZ).
- **Tiền:** `*_minor` (minor units) + `currency` ISO-4217. JPY zero-decimal → KHÔNG ×100.
- **ID:** UUID v4 sinh ở app. **Thời gian:** `TIMESTAMPTZ` (UTC).
- **Database-per-service:** không JOIN xuyên service; tham chiếu bằng id.
- **Outbox + Debezium:** service phát event có bảng `outbox` cùng khuôn → Kafka.
- **Thanh toán:** Stripe THẬT, một tài khoản, settle qua webhook. KHÔNG mock.

## Event Kafka
| Topic | Payload | Producer → Consumer |
|---|---|---|
| `payment.events` | PaymentSettled `{orderId,paymentId,status,ref}` | Payment → Order |
| `order.events` | OrderCompleted `{orderId,userId,email,eventId,ticketTypeId,quantity,amountMinor,currency}` | Order → Ticket, Notification |
| `user.events` | UserRegistered `{userId,email}` | Auth → Notification |
