# Quy ước path API — thống nhất & phân biệt rõ public / admin / internal

> **Mục đích:** một quy ước DUY NHẤT cho đường dẫn, để nhìn path là biết ngay route đó
> dành cho ai và đi qua đâu — tránh nhầm public với nội bộ. Bám sát code đang chạy
> (gateway routes, controller, `common-security`, nginx). Liên quan:
> [`SECURITY-ACCESS-CONTROL.md`](./SECURITY-ACCESS-CONTROL.md), [`/CLAUDE.md`](../CLAUDE.md).

---

## 1. Bốn nhóm path — nhìn tiền tố là biết loại

| Tiền tố | Dành cho | Đi qua | Auth |
|---------|----------|--------|------|
| `/api/<service>/public/…` | Khách vãng lai (chưa đăng nhập) | **Gateway** | **KHÔNG cần token** |
| `/api/<service>/…` | User đã đăng nhập | **Gateway** | **Cần JWT** (mặc định) |
| `/api/<service>/admin/…` | Quản trị viên | **Gateway** | JWT + role `ADMIN` (service kiểm) |
| `/internal/…` | **Service ↔ service** | **Gọi thẳng DNS, KHÔNG qua gateway** | Rào ở tầng mạng (xem §4) |
| `/webhooks/<provider>/…` | Hệ ngoài gọi vào (Stripe) | **nginx/Ingress thẳng tới service, bỏ qua gateway** | Verify chữ ký provider (không JWT) |

Ngoài ra là path **nền tảng** (framework cố định, người tiêu thụ là hạ tầng chứ không phải
client hay service nghiệp vụ): `/actuator/**` (K8s probe + Prometheus scrape), `/v3/api-docs`
+ `/swagger-ui` (springdoc). Nhóm này KHÁC `/internal/`: path do framework quy định, không
di dời được, nên để thành allowlist riêng — đừng nhét vào `/internal/`.

> **JWKS là `/internal/`, không phải "nền tảng".** Endpoint công bố public key của Auth do
> ta tự kiểm soát path và CHỈ service↔service gọi (gateway/service tải về verify JWT), nên
> đặt ở `/internal/jwks` đúng quy ước — không phải `/oauth2/jwks` đặc cách. Phân biệt: cái gì
> ta tự viết & chỉ service gọi → `/internal/`; cái gì framework cố định & hạ tầng gọi → nền tảng.

> **Nguyên tắc vàng:** end-user gọi được → `/api/…`. Chỉ service gọi nhau → `/internal/…`.
> Hai thứ này **không bao giờ** dùng chung tiền tố.

### Public vs cần-JWT — nhìn path là biết, không đoán theo method

Mức truy cập **lộ rõ trên path** qua nhánh con cố định, không dựa vào HTTP method (dễ nhầm):

```
GET  /api/catalog/public/events     → CÔNG KHAI   (có "/public/" → không cần token)
GET  /api/order/{id}                → CẦN JWT     (không "/public/", không "/admin/")
POST /api/catalog/admin/events      → CẦN ADMIN   (có "/admin/")
```

Quy tắc dựng luật (đồng nhất mọi service):

| Path | Mức |
|------|-----|
| `/api/<service>/public/**` | `PERMIT_ALL` |
| `/api/<service>/admin/**` | `hasRole(ADMIN)` |
| `/api/<service>/**` (còn lại) | `AUTHENTICATED` (mặc định) |

Ví dụ thật đang chạy:
- **Public:** `POST /api/auth/public/login`, `GET /api/catalog/public/events`.
- **Cần JWT:** `GET /api/auth/me`.
- **Admin:** `POST /api/catalog/admin/venues`.

Lợi ích so với "GET = public": ba nhánh **rời nhau theo path** nên không còn bẫy "đặt luật
admin trước luật GET", và ở gateway chỉ cần **một** luật `/api/*/public/**` = mở.

---

## 2. Quy ước tên segment `<service>`

`<service>` = **đúng tên service**, dùng y hệt ở mọi nơi:

| Service (`spring.application.name`) | DNS nội bộ | Route id (gateway) | Tiền tố path |
|---|---|---|---|
| `auth` | `auth:8081` | `auth` | `/api/auth` |
| `catalog` | `catalog:8082` | `catalog` | `/api/catalog` |
| `inventory` | `inventory:8083` | `inventory` | `/api/inventory` |
| `order` | `order:8084` | `order` | `/api/order` |
| `payment` | `payment:8086` | `payment` | `/api/payment` |
| `ticket` | `ticket:8087` | `ticket` | `/api/ticket` |
| `notification` | `notification:8088` | `notification` | `/api/notification` |
| `waitingroom` | `waitingroom:8089` | `waitingroom` | `/api/waitingroom` |

**Một cái tên cho tất cả** (artifact = DNS = route id = tiền tố path) → không phải nhớ
ánh xạ, không lẫn lộn. Vì segment là *tên service* (không phải resource collection) nên
**không tranh cãi số ít/số nhiều**: luôn singular theo tên service.

> Trước đây lệch: `/api/orders`, `/api/payments` (số nhiều), `/api/waiting-room` (gạch nối).
> Đã thống nhất về `/api/order`, `/api/payment`, `/api/waitingroom`.

Resource đặt SAU tiền tố, theo REST bình thường (số nhiều cho collection):
```
GET  /api/order/orders/{id}        # ❌ thừa "order" hai lần
GET  /api/order/{id}               # ✅ "đơn hàng id" — tiền tố đã là domain order
GET  /api/catalog/events           # ✅ collection events trong domain catalog
GET  /api/catalog/events/{id}/ticket-types
```
Tiền tố `/api/<service>` ĐÃ là tên miền nghiệp vụ; bên trong không lặp lại tên service.

---

## 3. Admin

Đặt dưới nhánh con cố định `admin` — ví dụ thật `/api/catalog/admin/events`,
`/api/catalog/admin/venues`. Hai lớp:
- **Gateway**: KHÔNG cần khai luật riêng — admin không nằm dưới `/public/` nên tự rơi vào
  mặc định `AUTHENTICATED` (chỉ cần có token ở biên).
- **Service** (`common-security`): `/api/<service>/admin/**` = `hasRole(ADMIN)`.

Vì `public`/`admin`/mặc-định là ba nhánh **rời nhau theo path**, thứ tự khai luật không
còn là bẫy (không như cách cũ "GET = public" phải đặt admin lên trước).

---

## 4. Internal — cách cụ thể cho route nội bộ

**Quy tắc cứng:** API service↔service đặt dưới tiền tố top-level **`/internal/`** (KHÔNG
nằm trong `/api`). Bên trong là resource service-local, **không lặp tên service** (vì đã
địa chỉ thẳng service đó qua DNS rồi):

```
Inventory expose:   POST /internal/holds          (giữ chỗ)
                    DELETE /internal/holds/{id}    (nhả chỗ - bù trừ saga)
Order gọi:          POST http://inventory:8083/internal/holds
```

Vì sao đủ an toàn mà không cần token nội bộ — **ba lớp**:
1. **Gateway KHÔNG khai route** cho `/internal/**` → từ Internet không có đường tới (rào chính).
2. **K8s NetworkPolicy** chỉ cho service trong cluster gọi tới cổng đó.
3. **`common-security`** để `/internal/**` = `permitAll` (vì lời gọi nội bộ không mang JWT
   người dùng; nếu bắt token sẽ chặn nhầm).

Checklist khi viết API nội bộ:
- [ ] Đặt dưới `/internal/…`, **không** dùng `/api/…`.
- [ ] **Không** thêm route `/internal/**` vào `app.gateway.access.rules` hay nginx.
- [ ] Phải có **NetworkPolicy** đi kèm trước khi lên prod (permitAll ở app chỉ an toàn khi
      mạng đã rào).
- [ ] Gọi nhau qua DNS nội bộ `http://<service>:<port>/internal/…`.

> Chi tiết cơ chế & sơ đồ luồng: [`SECURITY-ACCESS-CONTROL.md` §4](./SECURITY-ACCESS-CONTROL.md).

---

## 5. Webhook (ngoại lệ có chủ đích)

```
/webhooks/<provider>/…        ví dụ: /webhooks/stripe
```
- **Bỏ qua gateway**: nginx (dev) / Ingress (prod) trỏ thẳng tới Payment — đây là "reverse
  proxy DMZ".
- Xác thực bằng **chữ ký của provider** (Stripe webhook secret), KHÔNG phải JWT.
- Không đặt dưới `/api/` (không phải nghiệp vụ do client gọi).

---

## 6. Thêm service / endpoint mới — nhớ gì

1. Path nghiệp vụ: `/api/<service>/…` (tiền tố = tên service). Admin: `…/admin/…`.
2. Khai route ở gateway: `Path=/api/<service>/**`, route id = `<service>`.
3. Khai luật phân quyền bằng properties (xem
   [`SECURITY-ACCESS-CONTROL.md` §3](./SECURITY-ACCESS-CONTROL.md)) — không viết SecurityConfig.
4. Cần gọi nội bộ? → `/internal/…` + NetworkPolicy. **Không** route ra gateway.
5. nginx không phải sửa cho từng service: `/api/` là catch-all → gateway.
