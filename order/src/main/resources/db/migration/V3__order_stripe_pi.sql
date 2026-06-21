-- Lưu tham chiếu PaymentIntent của đơn (tiện tra cứu/đối soát với Payment & Stripe).
ALTER TABLE orders ADD COLUMN stripe_pi_id VARCHAR(255);
