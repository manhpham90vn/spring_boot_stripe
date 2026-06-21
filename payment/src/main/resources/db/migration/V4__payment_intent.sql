-- Luồng Stripe thật (bất đồng bộ): lưu tham chiếu PaymentIntent + trạng thái trung gian.
-- status (VARCHAR(32)) nay nhận thêm PROCESSING | CANCELED — không cần đổi DDL cột.
ALTER TABLE payments ADD COLUMN stripe_pi_id VARCHAR(255);
CREATE INDEX ix_payments_pi ON payments (stripe_pi_id);
