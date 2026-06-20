-- Payment = cổng DUY NHẤT gọi Stripe. Walking skeleton: 1 bản ghi thanh toán / đơn.
-- amount theo MINOR UNITS + currency (JPY zero-decimal → KHÔNG ×100). Xem payment-stripe-flow.md.
CREATE TABLE payments (
    id            UUID         PRIMARY KEY,
    order_id      UUID         NOT NULL UNIQUE,        -- 1 payment / đơn (idempotency)
    amount_minor  BIGINT       NOT NULL CHECK (amount_minor >= 0),
    currency      VARCHAR(3)   NOT NULL,
    status        VARCHAR(32)  NOT NULL,               -- PENDING | SUCCEEDED | FAILED
    payment_ref   VARCHAR(255),                        -- tham chiếu Stripe PaymentIntent / mock
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
