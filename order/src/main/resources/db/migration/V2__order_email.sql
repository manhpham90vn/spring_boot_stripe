-- Lưu email người mua (từ claim JWT) trên đơn → để phát OrderCompleted (Notification gửi
-- xác nhận) ở bước saga BẤT ĐỒNG BỘ (onPaymentSettled), khi không còn request gốc.
ALTER TABLE orders ADD COLUMN email VARCHAR(320);
