# 2. Catalog service (`catalog/`)

## Trách nhiệm
Trả lời "**có gì để bán**": venue, event, ticket type (loại vé) + giá, seat map (cho
ghế ngồi). **Đọc nhiều ghi ít** → cache mạnh Redis. **KHÔNG giữ tồn kho** (Inventory)
và KHÔNG suy ra SOLD_OUT.

## Database `catalog`

### venues
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| name | VARCHAR(200) | NOT NULL |
| address | VARCHAR(500) | NULL |
| city | VARCHAR(120) | NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

### events
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| venue_id | UUID | NOT NULL FK→venues, `ix_events_venue` |
| title | VARCHAR(300) | NOT NULL |
| description | TEXT | NULL |
| status | VARCHAR(32) | NOT NULL — `DRAFT\|ON_SALE\|CLOSED\|CANCELLED`, `ix_events_status` |
| starts_at | TIMESTAMPTZ | NOT NULL |
| sales_start_at / sales_end_at | TIMESTAMPTZ | NULL — cửa sổ mở bán |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

### ticket_types
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| event_id | UUID | NOT NULL FK→events, `ix_ticket_types_event` |
| name | VARCHAR(120) | NOT NULL |
| description | VARCHAR(500) | NULL |
| **kind** | VARCHAR(16) | NOT NULL DEFAULT `GA` — `GA` \| `SEATED` |
| price_minor | BIGINT | NOT NULL CHECK ≥ 0 |
| currency | VARCHAR(3) | NOT NULL — ISO-4217 |
| max_per_order | INTEGER | NOT NULL DEFAULT 10 CHECK > 0 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

### seat_map  *(chỉ cho `kind=SEATED`)*
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| ticket_type_id | UUID | NOT NULL FK→ticket_types |
| section | VARCHAR(64) | NOT NULL |
| row_label | VARCHAR(16) | NOT NULL |
| seat_number | VARCHAR(16) | NOT NULL |
| | | UNIQUE(ticket_type_id, section, row_label, seat_number) |

> Bản đồ ghế chỉ **mô tả** (tĩnh). Trạng thái bán từng ghế thuộc Inventory.

## Cache (Redis)
| Key | Nội dung | Evict khi |
|---|---|---|
| `cat:event:{id}` | chi tiết event + ticketTypes | admin sửa event/ticket type |
| `cat:events:onsale` | danh sách ON_SALE | đổi status event |
| `cat:seatmap:{ticketTypeId}` | seat map | admin sửa seat map |

## API — public (đọc)
| Method | Path | Response |
|---|---|---|
| GET | `/api/catalog/public/events` | `[EventSummary]` (ON_SALE) |
| GET | `/api/catalog/public/events/{id}` | `EventDetail{...,ticketTypes:[TicketType]}` · 404 |
| GET | `/api/catalog/public/events/{eventId}/ticket-types` | `[TicketType]` |
| GET | `/api/catalog/public/events/{eventId}/ticket-types/{ttId}` | `TicketType` · 404 |
| GET | `/api/catalog/public/venues` · `/venues/{id}` | `[Venue]` · `Venue`/404 |

`TicketType` JSON: `{id,name,description,kind,priceMinor,currency,maxPerOrder}`.

## API — admin (ghi, role ADMIN)
| Method | Path | Ghi chú |
|---|---|---|
| POST/PUT/DELETE | `/api/catalog/admin/venues[/{id}]` | 201/200/204 |
| GET/POST/PUT/DELETE | `/api/catalog/admin/events[/{id}]` | |
| PUT | `/api/catalog/admin/events/{id}/status` | `{status}` — chuyển DRAFT↔ON_SALE↔CLOSED↔CANCELLED |
| POST/PUT/DELETE | `/api/catalog/admin/events/{id}/ticket-types[/{ttId}]` | tạo cả seat_map khi `kind=SEATED` |

## Invariant / guard
- Sửa giá/loại vé khi event **đã ON_SALE** phải guard ở service (tránh đổi giá giữa
  phiên bán). Hard-delete chỉ khi `DRAFT`.
- Đổi `status` → **evict cache** public tương ứng (đảm bảo danh sách ON_SALE đúng).
- Tạo ticket type SEATED phải kèm seat_map; số ghế = nguồn để Inventory seed tồn.

## Phối hợp
- Khi mở bán, **Inventory** seed tồn theo `ticketTypeId` (GA: total_qty; SEATED: danh
  sách seat từ seat_map). Catalog không gọi Inventory trực tiếp — admin/seed job làm.

## Config
`server.port=8082`, DB `catalog`, Redis cache. Seat map tĩnh có thể phục vụ qua
CDN/Nginx.
