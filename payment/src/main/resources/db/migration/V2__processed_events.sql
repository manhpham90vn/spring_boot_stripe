-- Idempotency cho WEBHOOK Stripe (payment_issue.md 2.8): lưu event.id đã xử lý để bỏ qua
-- nếu Stripe gửi lại (webhook at-least-once, có thể trùng / sai thứ tự).
CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,   -- Stripe Event.id (vd evt_...)
    event_type   VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);
