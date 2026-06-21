# 7. Notification service (`notification/`)

## Trách nhiệm
Gửi email/SMS (xác nhận đơn + đính kèm vé/QR, welcome đăng ký). Gần như **stateless**,
**consume Kafka** thuần. Không có endpoint nghiệp vụ ra biên.

## Database `notification`
Chủ yếu để **idempotency + audit** (tránh gửi trùng khi event lặp — Kafka at-least-once).

### sent_notifications
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| dedup_key | VARCHAR(255) | NOT NULL **UNIQUE** — vd `order-completed:{orderId}`, `welcome:{userId}` |
| channel | VARCHAR(16) | NOT NULL — `EMAIL` \| `SMS` |
| recipient | VARCHAR(320) | NOT NULL |
| template | VARCHAR(64) | NOT NULL — vd `ORDER_CONFIRMATION`, `WELCOME` |
| status | VARCHAR(16) | NOT NULL — `SENT` \| `FAILED` |
| error | VARCHAR(500) | NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

> Trước khi gửi: `INSERT ... ON CONFLICT(dedup_key) DO NOTHING` → nếu đã có thì bỏ qua
> (đã gửi). Đảm bảo **mỗi event gửi đúng một lần**.

## Consume Kafka
| Topic | Event | Hành động |
|---|---|---|
| `order.events` | OrderCompleted | gửi `ORDER_CONFIRMATION` tới `email`, đính kèm link/QR vé (lấy từ Ticket nếu cần) |
| `user.events` | UserRegistered | gửi `WELCOME` (tùy chọn) |

## API
Không có route public/JWT. (Tùy chọn) `/internal/notifications` để admin/test gửi thủ công.

## Gửi & retry
- Provider email: dev **Mailpit**, prod SMTP/SendGrid... cấu hình qua property.
- Lỗi gửi tạm thời → retry backoff (Resilience4j); hết retry → ghi `FAILED` + alert,
  không chặn consumer (commit offset, xử lý lại qua job/DLT nếu cần).

## Config
`server.port=8088`, DB `notification`, `spring.mail.*` (Mailpit dev). Consumer
`order.events`, `user.events`.

## Lưu ý
- Không nhúng dữ liệu nhạy cảm thừa vào email; QR là ảnh ký số, không lộ secret.
- Đính kèm vé: hoặc gọi Ticket `/api/ticket/{id}/qr.png` (service token) hoặc link
  có thời hạn — quyết định ở bước triển khai template.
