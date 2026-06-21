-- Idempotency + audit cho thông báo: mỗi event (Kafka at-least-once) gửi ĐÚNG MỘT LẦN.
-- dedup_key UNIQUE = chốt idempotency (vd "order-completed:<orderId>", "welcome:<userId>").
CREATE TABLE sent_notifications (
    id          UUID PRIMARY KEY,
    dedup_key   VARCHAR(255) NOT NULL UNIQUE,
    channel     VARCHAR(16)  NOT NULL,            -- EMAIL | SMS
    recipient   VARCHAR(320) NOT NULL,
    template    VARCHAR(64)  NOT NULL,            -- ORDER_CONFIRMATION | WELCOME
    status      VARCHAR(16)  NOT NULL,            -- SENT | FAILED
    error       VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL
);
