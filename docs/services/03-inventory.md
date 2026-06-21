# 3. Inventory service (`inventory/`)

> Sâu hơn: [`inventory-no-oversell.md`](../flows/inventory-no-oversell.md) — atomic Redis, reconciliation, TTL hold theo phương thức.

## Trách nhiệm
Trả lời "**còn bao nhiêu, ghế nào trống**". **Service tranh chấp cao nhất.**
- **PostgreSQL = nguồn sự thật bền** (tổng, đã bán/SOLD).
- **Redis = lớp nóng** giữ chỗ tạm thời lúc flash sale (counter GA, seat hold).
Chỉ phục vụ `/internal/**` (Order saga gọi); không có endpoint công khai.

## Database `inventory`

### ticket_stock  *(GA — vé đứng)*
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| ticket_type_id | UUID | PK (= Catalog.TicketType.id) |
| event_id | UUID | NOT NULL, `ix_ticket_stock_event` |
| total_qty | INTEGER | NOT NULL CHECK ≥ 0 |
| sold_qty | INTEGER | NOT NULL DEFAULT 0 CHECK ≥ 0 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |
| | | CHECK `sold_qty ≤ total_qty` (`ck_sold_le_total`) |

### seat_inventory  *(SEATED — ghế ngồi)*
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| seat_id | UUID | PK (= Catalog.seat_map.id) |
| ticket_type_id | UUID | NOT NULL |
| event_id | UUID | NOT NULL, index |
| status | VARCHAR(16) | NOT NULL — `AVAILABLE` \| `SOLD` |
| order_id | UUID | NULL — đơn đã chốt ghế |
| updated_at | TIMESTAMPTZ | NOT NULL |

## Redis keyspace (hot path)
| Key | Kiểu | Thao tác | Mục đích |
|---|---|---|---|
| `inv:ga:{ticketTypeId}` | string(int) | `DECRBY n` (hold) / `INCRBY n` (nhả) | "còn lại" GA; seed = total−sold |
| `inv:hold:{holdId}` | hash | `HSET` + `EXPIRE` (TTL ~10') | nội dung 1 lần giữ chỗ (ttId, qty hoặc seatIds, orderId) |
| `inv:seat:{seatId}` | string | `SET NX` + TTL | giữ ghế tạm; chủ hold mới xóa được |

**Quy tắc no-oversell** (xem `inventory-no-oversell.md`):
- GA: `DECRBY`; nếu kết quả < 0 → `INCRBY` hoàn lại + báo hết (atomic, có thể dùng Lua).
- SEATED: `SET NX` từng ghế; ghế nào fail → nhả các ghế đã set trong cùng hold.

## API — internal
| Method | Path | Request | Response | Lỗi |
|---|---|---|---|---|
| PUT | `/internal/stock/{ticketTypeId}` | GA `{eventId,totalQty}` · SEATED `{eventId,seatIds[]}` | 200 | – |
| GET | `/internal/stock/{ticketTypeId}` | – | `{ticketTypeId,available,totalQty,soldQty}` | 404 chưa seed |
| POST | `/internal/holds` | GA `{ticketTypeId,quantity}` · SEATED `{ticketTypeId,seatIds[]}` | `{holdId, expiresAt}` | **409** `InsufficientStock` |
| POST | `/internal/holds/{holdId}/commit` | – | 200 (ghi SOLD bền: `sold_qty += n` / seat `SOLD`) | 404 hold hết hạn |
| DELETE | `/internal/holds/{holdId}` | – | 204 (compensating: nhả Redis, không đụng SOLD) | 404 |

## Vòng đời giữ chỗ
```
hold  → Redis giảm còn-lại + tạo inv:hold:{id} (TTL)         [chưa bền]
commit→ Postgres sold_qty/seat=SOLD (nguồn sự thật) + xóa hold
delete→ nhả còn-lại về Redis + xóa hold (đơn lỗi/timeout)
TTL hết (không commit/delete) → Redis tự nhả; reconciliation dọn
```

## Job nền
`StockReconciliationJob`: định kỳ dựng lại `inv:ga:{ttId}` = `total_qty − sold_qty`
từ Postgres (khi restart/nghi lệch); với SEATED kiểm các `inv:seat` mồ côi.

## Invariant
- **Postgres là nguồn sự thật**, Redis chỉ tăng tốc — khi lệch luôn tin Postgres.
- `commit` idempotent theo `holdId` (commit lại không cộng SOLD hai lần).
- Không bao giờ để `sold_qty > total_qty` (DB CHECK chặn cứng tuyến cuối).

## Config
`server.port=8083`, DB `inventory`, Redis (cluster/Sentinel HA). TTL hold cấu hình
`inventory.hold.ttl`.
