# 8. Waiting Room service (`waitingroom/`)

> Sâu hơn: [`waiting-room.md`](../flows/waiting-room.md) — cơ chế sorted set, admission theo tồn kho, chống bot.

## Trách nhiệm
**Van bảo vệ trước spike** flash sale. Xếp người vào hàng đợi, thả vào (admission)
theo **nhịp hạ nguồn (và Stripe) chịu được**, kèm CAPTCHA chống bot. On-prem không
autoscale → đây là cơ chế hấp thụ đỉnh tải. **Store chính: Redis** (không PostgreSQL).

## Redis keyspace
| Key | Kiểu | Ý nghĩa |
|---|---|---|
| `wr:events` | SET | eventId đang có hàng (AdmissionJob duyệt) |
| `wr:seq:{eventId}` | string(int) | bộ đếm tăng dần → score ZSET (FIFO công bằng) |
| `wr:queue:{eventId}` | ZSET (score = seq) | hàng đợi chờ; vị trí = rank |
| `wr:token:{token}` | string `{eventId}` | TTL — định danh 1 chỗ trong hàng |
| `wr:admit:{eventId}:{token}` | string | TTL — "đã được vào" (PASS), cho phép gọi `/api/order` |
| `wr:rate:{eventId}` | string(int) | nhịp thả per-event (override `wr:config`) |
| `wr:soldout:{eventId}` | string | cờ hết vé → ngừng thả |
| `wr:config` | HASH | cấu hình admission động (rate/tokenTtlSeconds/admitTtlSeconds) |
| `wr:captcha:{captchaId}` | string | TTL — đáp án CAPTCHA (one-time) |

## API
| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| GET | `/api/waitingroom/public/captcha` | – | – | PNG + header `X-Captcha-Id` |
| POST | `/api/waitingroom/public/{eventId}/enqueue` | – (+CAPTCHA) | `{captchaId, captchaAnswer}` | `{token, position, etaSeconds}` |
| GET | `/api/waitingroom/public/{eventId}/status` | – | `?token=` | `{position, admitted, accessToken?, soldOut}` |
| GET | `/internal/admission/{eventId}/check` | mạng | `?token=` | `{valid}` |
| POST | `/internal/events/{eventId}/rate` | mạng | `?rate=` | 204 |
| POST | `/internal/events/{eventId}/soldout` | mạng | – | 204 |
| GET / PUT | `/api/waitingroom/admin/config` | ADMIN | `{rate, tokenTtlSeconds, admitTtlSeconds}` | cùng |

- `captcha`: sinh con số ngẫu nhiên, vẽ ra ảnh PNG, lưu đáp án `wr:captcha:{id}` (TTL ngắn,
  one-time). Client hiển thị ảnh, bắt người dùng nhập lại số.
- `enqueue`: verify CAPTCHA (đối chiếu `captchaAnswer` với `wr:captcha:{captchaId}`) → ZADD vào
  `wr:queue:{eventId}` (score = INCR `wr:seq` cho FIFO công bằng) → trả `token` + vị trí.
- `status`: trả vị trí hiện tại; tới lượt → `admitted:true` + `accessToken` (PASS) để hạ nguồn
  cho phép đặt mua; hết vé/hết hạn → `soldOut:true`.
- `internal/admission/check`: Order verify PASS trước khi nhận `POST /api/order`.
- `internal/.../rate` & `.../soldout`: Order/Inventory chỉnh nhịp thả per-event / báo hết vé.
- `admin/config`: chỉnh cấu hình admission ĐỘNG từ trang admin (xem "Cấu hình động" bên dưới).

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
`server.port=8089`, Redis (HA). `waitingroom.admission.rate`, `*.token-ttl`,
`*.admit-ttl`, `*.tick`. Client: `InventoryClient` (đọc tồn). KHÔNG có PostgreSQL.
Bảo mật: verify JWT để gác `/admin/**` (role ADMIN) — reactive `SecurityConfig`, KHÔNG dùng
`common-security` (vốn chỉ cho servlet). `JWK_SET_URI` trỏ JWKS của Auth.

## Cấu hình động — NGUỒN DUY NHẤT là Redis (chỉnh từ admin, KHÔNG redeploy)
`rate`, `tokenTtlSeconds`, `admitTtlSeconds` sống ở Redis hash **`wr:config`** — đây là nguồn
DUY NHẤT lúc chạy. `application.properties` chỉ là **giá trị seed lần đầu**: lúc khởi động
`AdmissionConfigSeeder` ghi vào `wr:config` bằng `HSETNX` (chỉ field còn thiếu, KHÔNG đè giá trị
admin). Sau đó mọi lần đọc lấy thẳng Redis, không merge lại properties → tránh "hai nơi". Nếu
Redis bị flush, lần đọc kế tiếp tự seed lại từ properties.

Admin sửa qua `PUT /api/waitingroom/admin/config` → bộ thả áp dụng ngay. Lưu Redis (KHÔNG
PostgreSQL) để giữ đúng ràng buộc "Store chính: Redis" và để mọi replica thấy cùng một cấu hình.
`tick` (chu kỳ bộ thả) KHÔNG lưu Redis — nhịp scheduler cố định ở properties, không phải knob
nghiệp vụ. Per-event vẫn override `rate` qua `wr:rate:{eventId}` (ưu tiên cao hơn `wr:config`).

## Lưu ý triển khai
- Làm sau khi luồng mua lõi đã chạy (cần biết throughput thật để đặt rate).
- ZSET cho thứ tự công bằng (FIFO theo thời điểm vào hàng); cân nhắc sharding theo
  `eventId` cho sự kiện cực lớn.
