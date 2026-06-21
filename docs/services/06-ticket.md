# 6. Ticket service (`ticket/`)

## Trách nhiệm
Quản lý **vé ĐÃ PHÁT** (khác ticket type ở Catalog). Consume `OrderCompleted` →
phát vé + sinh **QR code ký số**. Validate khi quét tại cổng (một-vé-một-lượt-vào).

## Database `ticket`

### issued_tickets
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| order_id | UUID | NOT NULL, `ix_issued_tickets_order` |
| user_id | UUID | NOT NULL, `ix_issued_tickets_user` (IDOR) |
| event_id | UUID | NOT NULL |
| ticket_type_id | UUID | NOT NULL |
| seat_id | UUID | NULL — nếu SEATED |
| seat_label | VARCHAR(32) | NULL — in lên vé (vd "A-12") |
| qr_token | TEXT | NOT NULL — `"<ticketId>.<HMAC-SHA256>"` |
| status | VARCHAR(16) | NOT NULL — `VALID` \| `USED` |
| issued_at | TIMESTAMPTZ | NOT NULL |
| used_at | TIMESTAMPTZ | NULL |

> Idempotency phát vé: trước khi phát kiểm `existsByOrderId(orderId)` → đúng 1 lần/đơn.

## QR ký số (bảo mật)
- **Ký ở backend** bằng HMAC-SHA256 với secret `ticket.qr.secret` (Secret K8s).
  `qr_token = "<ticketId>.<base64url(HMAC(ticketId))>"`.
- Render PNG bằng ZXing, phục vụ qua `/api/ticket/{id}/qr.png`.
- **Verify** khi quét: kiểm chữ ký (so sánh hằng-thời-gian) TRƯỚC → tra vé → kiểm `VALID`.
- FE chỉ hiển thị `<img>`; KHÔNG giữ secret, KHÔNG sinh QR (chống vé giả).

## API
| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| GET | `/api/ticket` | JWT | – | `[TicketResponse]` của chính mình |
| GET | `/api/ticket/{id}/qr.png` | JWT (chủ vé) | – | 200 image/png · **404** nếu không thuộc mình (IDOR) |
| POST | `/internal/validate` | internal (app cổng) | `{qrToken}` | `{valid, ticketId?, eventId?, reason?}` |

`TicketResponse`: `{id,orderId,eventId,ticketTypeId,seatLabel?,status,qrToken,issuedAt}`.

## Consume Kafka
`OrderCompleted {orderId,userId,email,eventId,ticketTypeId,quantity,...}` từ
`order.events` → tạo `quantity` vé (SEATED: 1 vé/ghế, gắn seat_id/seat_label) →
ký QR → lưu. Idempotent theo `orderId`.

## Validate tại cổng
```
verify(qrToken) sai chữ ký       → {valid:false, "Chữ ký QR không hợp lệ"}
vé không tồn tại                 → {valid:false, "Vé không tồn tại"}
status=USED                      → {valid:false, "Vé đã được sử dụng"}
status=VALID → markUsed()        → {valid:true, ticketId, eventId}
```
`markUsed` idempotent-an toàn: chỉ chuyển khi đang `VALID` (chống dùng lại / double-scan).

## Config
`server.port=8087`, DB `ticket`, `ticket.qr.secret` (Secret). Consumer `order.events`.
Nâng cấp tương lai: đổi HMAC → ký bất đối xứng (RS256) để cổng chỉ cần public key.
