# 8. Waiting Room service (`waitingroom/`)

> Sâu hơn: [`waiting-room.md`](../flows/waiting-room.md) — cơ chế sorted set, admission theo tồn kho, chống bot.

## Trách nhiệm
**Van bảo vệ trước spike** flash sale. Xếp người vào hàng đợi, thả vào (admission)
theo **nhịp hạ nguồn (và Stripe) chịu được**, kèm CAPTCHA chống bot. On-prem không
autoscale → đây là cơ chế hấp thụ đỉnh tải. **Store chính: Redis** (không PostgreSQL).

## Redis keyspace
| Key | Kiểu | Ý nghĩa |
|---|---|---|
| `wr:queue:{eventId}` | ZSET (score = seq/timestamp) | hàng đợi chờ; vị trí = rank |
| `wr:token:{token}` | string `{userId,eventId}` | TTL — định danh 1 người trong hàng |
| `wr:admit:{eventId}:{token}` | string | TTL — "đã được vào", cho phép gọi `/api/order` |
| `wr:rate:{eventId}` | string(int)/config | nhịp thả mỗi giây (admission rate) |
| `wr:captcha:{token}` | string | TTL — đã qua CAPTCHA |

## API
| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/api/waitingroom/public/{eventId}/enqueue` | – (+CAPTCHA) | `{captchaToken}` | `{token, position, etaSeconds}` |
| GET | `/api/waitingroom/public/{eventId}/status` | – | `?token=` | `{position, admitted, accessToken?}` |

- `enqueue`: verify CAPTCHA → thêm vào `wr:queue:{eventId}` (ZADD) → trả `token` + vị trí.
- `status`: trả vị trí hiện tại; khi tới lượt → `admitted:true` + `accessToken` (token
  admission) để hạ nguồn cho phép đặt mua.

## Cơ chế admission (drip)
```
Job định kỳ mỗi event:
  rate = wr:rate:{eventId}  (đặt theo throughput Order/Inventory/Stripe)
  còn tồn? (hỏi Inventory GET /internal/stock) — HẾT thì NGỪNG thả
  pop N = rate phần tử đầu ZSET (ZPOPMIN) → tạo wr:admit:{eventId}:{token} (TTL)
```
- **Admission rate phải biết tồn kho còn lại** — không thả thêm khi sắp/đã hết vé
  (tránh đẩy người vào tranh nhau con số 0).
- Token admission có **TTL**: vào trễ quá hạn → phải xếp lại (chống giữ chỗ vô hạn).

## Phối hợp chặn ở biên
Khi flash sale bật, **Order/Gateway** yêu cầu `accessToken` admission hợp lệ mới nhận
`POST /api/order` (verify với `wr:admit:*`). Ngoài flash sale có thể tắt van.

## Chống bot
- CAPTCHA bắt buộc ở `enqueue` (lưu `wr:captcha:{token}` khi đạt).
- Rate limit theo IP ở gateway cho route waitingroom.

## Config
`server.port=8089`, Redis (HA). `waitingroom.admission.rate`, `*.token.ttl`,
`*.admit.ttl`. Client: `InventoryClient` (đọc tồn). KHÔNG có PostgreSQL.

## Lưu ý triển khai
- Làm sau khi luồng mua lõi đã chạy (cần biết throughput thật để đặt rate).
- ZSET cho thứ tự công bằng (FIFO theo thời điểm vào hàng); cân nhắc sharding theo
  `eventId` cho sự kiện cực lớn.
