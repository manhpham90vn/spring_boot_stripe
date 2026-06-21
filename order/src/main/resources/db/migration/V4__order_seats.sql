-- SEATED: ghế đã chọn của đơn (= Catalog.seat_map.id). NULL với đơn GA.
ALTER TABLE orders ADD COLUMN seat_ids UUID[];
