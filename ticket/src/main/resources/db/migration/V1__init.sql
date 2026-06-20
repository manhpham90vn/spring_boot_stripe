-- Ticket = "vé ĐÃ PHÁT" (khác ticket type ở Catalog). Sinh sau khi đơn hoàn tất.
-- Mỗi vé có QR token ký số; trạng thái dùng khi quét tại cổng.
CREATE TABLE issued_tickets (
    id             UUID         PRIMARY KEY,
    order_id       UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    event_id       UUID         NOT NULL,
    ticket_type_id UUID         NOT NULL,
    qr_token       TEXT         NOT NULL,           -- "<ticketId>.<HMAC>" — ký số, verify khi quét
    status         VARCHAR(16)  NOT NULL,           -- VALID | USED
    issued_at      TIMESTAMPTZ  NOT NULL,
    used_at        TIMESTAMPTZ
);
CREATE INDEX ix_issued_tickets_order ON issued_tickets (order_id);
CREATE INDEX ix_issued_tickets_user  ON issued_tickets (user_id);
