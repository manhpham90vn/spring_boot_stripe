-- SEATED: vé gắn với một ghế cụ thể. seat_id = Catalog.seat_map.id; seat_label in lên vé (vd "A-12").
ALTER TABLE issued_tickets ADD COLUMN seat_id    UUID;
ALTER TABLE issued_tickets ADD COLUMN seat_label VARCHAR(32);
