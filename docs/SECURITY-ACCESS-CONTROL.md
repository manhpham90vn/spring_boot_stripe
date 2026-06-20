# Kiểm soát truy cập — Authentication, Authorization & Internal-only

> **Mục đích:** giải thích MÔ HÌNH kiểm soát truy cập của hệ thống và **bám sát code
> đang chạy** sau đợt gom luật về một nguồn. Chi tiết cơ chế xác thực JWT (ký RS256,
> JWKS, verify) nằm ở [`jwt-authentication.md`](./jwt-authentication.md); ràng buộc gốc
> ở [`/CLAUDE.md`](../CLAUDE.md).
>
> **TL;DR:** Tách rõ 3 việc. **Authentication** (token có thật không) — cơ chế dùng chung
> ở `common-security` + gateway. **Authorization** (ai được vào route nào) — khai báo bằng
> *dữ liệu* trong `application.properties`, một nguồn cho mỗi nơi, **hai điểm thực thi**
> (biên + service). **Internal-only** (route service↔service) — chặn ở *tầng mạng*
> (gateway không route + K8s NetworkPolicy), không phải bằng token người dùng.

---

## 1. Ba mối quan tâm — đừng trộn vào nhau

| Việc | Câu hỏi nó trả lời | Cơ chế | Nơi khai báo |
|------|--------------------|--------|--------------|
| **Authentication** | "Token này có thật & còn hạn không?" | Verify chữ ký RS256 bằng public key (JWKS), kiểm `iss`/`exp` | Gateway (`NimbusReactiveJwtDecoder`) + mỗi service (`common-security`) |
| **Authorization** | "Danh tính này được vào route nào?" | So path/method/role với bảng luật | Gateway: `app.gateway.access.rules` · Service: `app.security.rules` |
| **Internal-only** | "Route này có được gọi từ ngoài không?" | Không expose ra biên + chặn ở tầng mạng | Gateway không khai route + K8s NetworkPolicy + quy ước `/internal/**` |

Sai lầm thường gặp là dồn cả ba vào một `if` trong code nghiệp vụ. Ở đây mỗi việc một
cơ chế, khai báo tách bạch.

---

## 2. Authentication — đã gom sẵn (nhắc lại ngắn)

- **Auth** ký access token bằng **private key RS256**, gắn `kid`, `iss=auth`, `sub=userId`,
  `roles=[...]`, `exp`.
- **Gateway** và **mọi service** verify **cục bộ** bằng **public key** tải từ JWKS của Auth
  (`/internal/jwks` — endpoint nội bộ, xem §4), **cache lại** — KHÔNG gọi Auth mỗi request
  (tránh chẹn event-loop lúc flash sale).
- Danh tính đến từ token đã xác thực bằng mật mã → **không tin** bất kỳ header nào do
  client/gateway gắn vào. Vì thế dù bị gọi thẳng trong cluster, service vẫn an toàn.

> Đây là lý do verify được lặp ở **2 lớp** (gateway + service) — *defense in depth*. Chi
> tiết đầy đủ: [`jwt-authentication.md`](./jwt-authentication.md).

---

## 3. Authorization — một nguồn khai báo, hai điểm thực thi

### 3.1 Vì sao KHÔNG dồn hết về một chỗ

CLAUDE.md là bất biến: *"service cũng tự verify lại JWT… không tin tưởng mù quáng dù bị
gọi thẳng trong cluster"*. Nếu dồn toàn bộ phân quyền lên gateway rồi để service tin
gateway, thì một lời gọi nội bộ (Order→Inventory) hay kẻ đã lọt vào cluster sẽ **bỏ qua
hết luật**. Vậy **enforcement phải ở 2 lớp**. Cái gom được là **khai báo** (data) và
**code cơ chế** (`common-security`), để 2 lớp không còn viết tay rồi lệch nhau — chính
sự lệch đó từng tạo ra bug "gateway chặn cả khách duyệt catalog công khai".

### 3.2 Phân vai biên vs service

```
            ┌────────────────────────── BIÊN (gateway) ──────────────────────────┐
client ───► │  Edge-policy: PUBLIC vs CẦN-TOKEN  (KHÔNG xét role — tránh trùng)    │
            │  app.gateway.access.rules[...]                                       │
            └───────────────────────────────┬─────────────────────────────────────┘
                                            │ (đã qua biên)
            ┌───────────────────────────────▼───────── SERVICE (common-security) ──┐
            │  Authz chi tiết: method + role (vd ADMIN), của riêng service          │
            │  app.security.rules[...]                                              │
            └──────────────────────────────────────────────────────────────────────┘
```

- **Gateway chỉ phân biệt PUBLIC vs cần-token.** Không xét role, vì role là *nghiệp vụ của
  service* — bắt gateway biết "catalog admin cần ADMIN" sẽ làm gateway phải đổi mỗi khi
  service thêm endpoint, và luật role bị trùng ở 2 nơi.
- **Service giữ luật role/method.** Nó sở hữu tài nguyên nên biết rõ ai được làm gì.

### 3.3 Khai báo bằng dữ liệu (không còn SecurityConfig viết tay)

**Service** — khai trong `application.properties`, `common-security` đọc và dựng filter:

```properties
# Ví dụ thật của Catalog (catalog/src/main/resources/application.properties)
# Mức truy cập lộ rõ trên path (xem API-CONVENTIONS.md): public/admin là nhánh riêng.
app.security.rules[0].pattern=/api/catalog/admin/**    # khu admin: cần role ADMIN
app.security.rules[0].role=ADMIN                        # (đặt role là ngầm hiểu HAS_ROLE)
app.security.rules[1].pattern=/api/catalog/public/**   # nhánh công khai: không cần token
app.security.rules[1].access=PERMIT_ALL
# Không khớp luật nào → mặc định authenticated.
```

Mỗi luật (`ResourceServerProperties.Rule`):
- `pattern` (bắt buộc) — Ant pattern.
- `method` (tùy chọn) — GET/POST/...; bỏ trống = mọi method. (Theo quy ước path mới thường
  KHÔNG cần `method` nữa vì public/admin đã tách nhánh.)
- `access` — `PERMIT_ALL` | `AUTHENTICATED` | `HAS_ROLE`.
- `role` — đặt role là **ngầm hiểu** `HAS_ROLE` (bất kể `access`).

**Gateway** — khai trong `apigateway/src/main/resources/application.properties`. Nhờ quy
ước path, chỉ cần MỘT luật mở mọi nhánh public:

```properties
app.gateway.access.rules[0].pattern=/api/*/public/**       # mọi nhánh public của mọi service
app.gateway.access.rules[0].access=PERMIT_ALL              # (auth/public, catalog/public, ...)
# Còn lại → anyExchange = cần token: /api/auth/me, /api/catalog/admin/**, /api/order/**...
```

> **Hết bẫy thứ tự.** Vì `public`/`admin`/mặc-định rời nhau theo path, không còn phải lo
> "đặt admin trước GET" như cách cũ. Admin tự rơi vào `authenticated` ở biên (service kiểm role).

### 3.4 Cơ chế dựng filter (cùng một khuôn ở cả hai)

`common-security` (servlet) và gateway (reactive) cùng làm 4 bước, đăng ký theo thứ tự
= thứ tự xét (khớp trước thắng):

1. **Built-in luôn mở:** `/actuator/health|info|prometheus`, swagger; gateway thêm `/__fallback`.
2. **`/internal/**` → permitAll** (xem mục 4).
3. **`rules[...]`** theo đúng thứ tự khai báo → `permitAll` / `authenticated` / `hasRole`.
4. **anyRequest/anyExchange → authenticated.**

Code: `ResourceServerAutoConfiguration#resourceServerSecurityFilterChain` (servlet) và
`SecurityConfig#applyAccessRules` (gateway). Vai trò → authority: claim `roles=["ADMIN"]`
→ `ROLE_ADMIN`, nên `hasRole("ADMIN")` hoạt động.

### 3.5 Thêm một service mới cần làm gì

1. Thêm dependency `common-security`.
2. Đặt `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` + `app.security.issuer`.
3. Khai `app.security.rules[...]`. **Hết** — không viết SecurityConfig.
4. Nếu route cần ra ngoài: thêm `app.gateway.access.rules[...]` ở gateway (mặc định mọi
   route mới là cần-token, nên thường chỉ phải khai khi muốn nới PUBLIC).

---

## 4. Internal-only — chặn ở tầng mạng, không bằng token

### 4.1 Vấn đề

Saga sẽ có API service↔service (vd `Order` gọi `Inventory` để giữ chỗ). Những API này
**không bao giờ được gọi từ Internet**, nhưng lời gọi nội bộ thường **không mang JWT
người dùng** → không thể bảo vệ bằng `authenticated()`.

### 4.2 Cách đã chọn: quy ước `/internal/**` + rào ở tầng mạng

Ba lớp, không cần token nội bộ:

1. **Quy ước path:** mọi API nội bộ đặt dưới `/internal/**`.
2. **Gateway KHÔNG khai route** cho `/internal/**` → không có đường nào từ biên với tới
   (đây là rào chính, "không expose thì không tấn công được").
3. **K8s NetworkPolicy** chỉ cho phép traffic service↔service trong cluster tới cổng đó,
   chặn mọi nguồn khác (rào ở tầng mạng, độc lập với app).

Trong app, `common-security` để `/internal/**` là **permitAll** — vì nếu bắt token sẽ
chặn nhầm chính lời gọi nội bộ hợp lệ. Bảo vệ đến từ *mạng*, không phải từ JWT. (Auth dùng
SecurityConfig riêng nên tự khai `/internal/**` permitAll để giữ chuẩn đồng nhất.)

> **Ví dụ thật đang chạy: `/internal/jwks`.** Endpoint công bố public key của Auth đặt ở
> `/internal/jwks` (không phải `/oauth2/jwks`) vì chỉ gateway/service gọi để verify JWT.
> Đây là minh hoạ rõ nhất của quy ước: ta tự kiểm soát path + chỉ service↔service dùng → `/internal/`.

```
Internet ──► Gateway ──(không có route /internal)──►  ✗  (không tới được)

Order ───────(trong cluster, NetworkPolicy cho phép)──►  Inventory /internal/holds  ✓
Gateway ─────(trong cluster)──────────────────────────►  Auth /internal/jwks        ✓
Pod lạ ──────(NetworkPolicy chặn)────────────────────►  ✗
```

> **Vì sao không dùng shared-secret/mTLS lúc này?** Dự án là "K8s thuần", NetworkPolicy là
> công cụ sẵn có, đủ mạnh và không thêm secret/cert phải xoay vòng. Có thể nâng lên mTLS
> (service mesh) sau nếu cần xác thực danh tính service chứ không chỉ chặn mạng.

### 4.3 Lưu ý khi code API nội bộ

- Đặt đúng dưới `/internal/**` (đừng để lẫn với `/api/**` công khai).
- **Tuyệt đối không** thêm route `/internal/**` vào `app.gateway.access.rules`.
- Phải có NetworkPolicy đi kèm trước khi lên prod — `permitAll` ở app **chỉ an toàn khi**
  tầng mạng đã rào. Thiếu NetworkPolicy = API nội bộ phơi ra nếu ai đó lỡ thêm route.

---

## 5. Đối chiếu nhanh (request đi đâu, ai chặn)

| Request | Biên (gateway) | Service (common-security) | Kết quả |
|---------|----------------|---------------------------|---------|
| `POST /api/auth/public/login` | PERMIT_ALL (`/public/`) | (Auth tự cấu hình) | ✅ ai cũng gọi được |
| `GET /api/catalog/public/events` (khách) | PERMIT_ALL (`/public/`) | PERMIT_ALL (`/public/`) | ✅ duyệt không cần đăng nhập |
| `GET /api/auth/me` (có token) | cần token ✅ | (Auth verify) | ✅ trả thông tin user |
| `POST /api/catalog/admin/venues` (user thường) | cần token ✅ | `hasRole(ADMIN)` ✗ | ⛔ 403 tại service |
| `POST /api/catalog/admin/venues` (admin) | cần token ✅ | `hasRole(ADMIN)` ✅ | ✅ |
| `GET /api/order/123` (khách) | anyExchange → cần token ✗ | — | ⛔ 401 tại biên |
| `POST /internal/holds` từ Internet | không có route ✗ | — | ⛔ không tới được |
| `POST /internal/holds` từ Order (trong cluster) | (bỏ qua biên) | permitAll, NetworkPolicy ✅ | ✅ |
| Webhook Stripe `/webhooks/stripe` | **bỏ qua gateway** (Ingress riêng, DMZ) | verify chữ ký Stripe (không JWT) | ✅ |

---

## 6. File liên quan

| File | Vai trò |
|------|---------|
| `common-security/.../ResourceServerProperties.java` | Khai báo luật service (`issuer`, `publicPaths`, `rules`) |
| `common-security/.../ResourceServerAutoConfiguration.java` | Dựng filter chain + JwtDecoder dùng chung |
| `apigateway/.../config/GatewayAccessProperties.java` | Khai báo edge-policy |
| `apigateway/.../config/SecurityConfig.java` | Dựng SecurityWebFilterChain ở biên |
| `*/src/main/resources/application.properties` | Nơi mỗi nơi khai luật của mình |

> Webhook Stripe là ngoại lệ có chủ đích: vào thẳng Payment qua Ingress riêng (reverse
> proxy DMZ), verify **chữ ký Stripe** chứ không phải JWT — xem
> [`payment_issue.md`](./payment_issue.md).
