# Xác thực JWT — Stateless, khóa đối xứng/bất đối xứng & quy trình verify

> **Mục đích:** giải thích cơ chế xác thực của hệ thống và **bám sát code đang
> chạy** trong `auth/`, `apigateway/`, `common-security/`. Ràng buộc gốc ở
> [`/CLAUDE.md`](../CLAUDE.md); kiến trúc tổng quan ở [`architecture.md`](./architecture.md).
>
> **TL;DR triển khai hiện tại:** Auth ký token bằng **RS256 (bất đối xứng)**, công
> bố public key qua **JWKS** (`/internal/jwks`). Gateway và mọi service **verify cục
> bộ** bằng public key, **không gọi Auth mỗi request** → hệ thống **stateless**.

---

## 1. Stateless vs Stateful

Khác nhau ở chỗ: **server có lưu trạng thái phiên (session) của client giữa các
request hay không.**

### Stateful (session-based) — *không dùng trong hệ thống này*

- Khi login, server tạo một **session** lưu ở phía server (RAM/Redis/DB) và trả
  về một `sessionId` (thường qua cookie).
- Mỗi request sau đó client gửi `sessionId`; server **tra ngược** vào session
  store để biết "ai đang gọi, còn hợp lệ không".
- Trạng thái thật nằm ở server, `sessionId` chỉ là con trỏ.

| Ưu | Nhược |
|----|-------|
| Thu hồi (revoke) tức thì: xóa session là xong | Mỗi request phải tra session store → thêm I/O, thêm điểm chết |
| Token (sessionId) nhỏ, không lộ dữ liệu | Khó scale ngang: cần sticky session hoặc session store dùng chung |
| | Service A muốn biết user phải hỏi service Auth/session store |

### Stateless (token-based) — **hệ thống đang dùng**

- Khi login, Auth phát một **JWT** chứa sẵn danh tính (`sub`, `email`, `roles`)
  và **ký số**. Server **không lưu** gì về phiên.
- Mỗi request, client gửi JWT qua header `Authorization: Bearer <token>`. Người
  nhận chỉ cần **verify chữ ký + hạn dùng** là tin được token, **không cần tra
  cứu store nào**.
- "Sự thật về phiên" nằm ngay trong token, được bảo vệ bằng mật mã.

| Ưu | Nhược |
|----|-------|
| Verify cục bộ, không I/O ngoài → **scale ngang thoải mái** | Khó thu hồi trước hạn (token đã phát vẫn hợp lệ tới `exp`) |
| Mọi service tự xác thực, không phụ thuộc Auth lúc runtime | Payload lớn hơn sessionId, claim là dữ liệu công khai (chỉ ký, không mã hóa) |
| Hợp với microservices + flash sale (chục nghìn req/s) | Cần quản lý vòng đời khóa ký (rotation) |

### Vì sao dự án này chọn stateless

CLAUDE.md ràng buộc rõ: **on-prem không autoscale**, flash sale chục nghìn người
cùng lúc, và gateway **WebFlux/Netty thuần — tuyệt đối không add JPA/JDBC/DB**.
Nếu mỗi request phải gọi Auth hay tra session store thì:

1. Auth thành nút cổ chai + điểm chết duy nhất (SPOF) cho toàn bộ traffic.
2. Gateway buộc phải làm I/O blocking → chẹn event-loop Netty đúng lúc cao điểm.

→ Stateless với JWT verify cục bộ là lựa chọn bắt buộc. Cụ thể trong code:

```java
// auth/.../config/SecurityConfig.java   (Auth — servlet)
// common-security/.../ResourceServerAutoConfiguration.java   (các service khác)
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

`STATELESS` = Spring Security không tạo `HttpSession`, không đọc session cookie;
mỗi request được xác thực lại từ đầu chỉ dựa vào Bearer token.

> **Đánh đổi đang chấp nhận:** chưa có cơ chế revoke token trước hạn. Token sống
> tối đa `auth.jwt.access-token-ttl` (mặc định **1 giờ**, xem `JwtProperties`).
> Khi cần thu hồi sớm (đổi mật khẩu, ban user) sẽ thêm denylist (Redis) hoặc rút
> ngắn TTL + refresh token sau.

---

## 2. JWT cấu trúc nhanh

Một JWT gồm 3 phần Base64URL nối bằng dấu chấm: `header.payload.signature`.

- **Header** — thuật toán ký + `kid` (key id) để người verify biết dùng khóa nào.
- **Payload (claims)** — danh tính & metadata. Token hệ thống phát ra (xem
  `JwtServiceImpl`):

  ```jsonc
  {
    "iss": "auth",                 // issuer — ai phát token
    "sub": "<user-uuid>",          // subject — id người dùng
    "email": "user@example.com",
    "roles": ["USER"],             // dùng để phân quyền
    "iat": 1718800000,             // issued-at
    "exp": 1718803600              // expiry (iat + TTL)
  }
  ```

- **Signature** — chữ ký trên `header.payload`. **Đây là thứ chống giả mạo.**
  Claim không được mã hóa (ai cũng đọc được bằng base64-decode) — chỉ được **ký**,
  nên đừng nhét bí mật vào claim.

---

## 3. Khóa đối xứng vs bất đối xứng

"Đối xứng / bất đối xứng" nói về **khóa dùng để ký và để verify chữ ký**.

### 3.1. Đối xứng (HMAC — HS256)

- **Một khóa bí mật duy nhất (shared secret)** dùng cho **cả ký lẫn verify**.
- Ai verify được thì cũng **ký được** → ai cũng có thể tự phát token giả.

```
        secret (chung)                 secret (chung — y hệt)
ký:  HMAC(header.payload, secret) → sig
verify: HMAC(header.payload, secret) == sig ?
```

| Ưu | Nhược |
|----|-------|
| Nhanh, đơn giản, token nhỏ | Phải chia sẻ secret cho **mọi** bên verify |
| | Mọi bên giữ secret đều có quyền **phát** token → trong microservices, 1 service lộ secret là toàn hệ thống thủng |
| | Khó rotation: đổi secret phải đồng bộ tất cả service cùng lúc |

→ Hợp với monolith hoặc khi chỉ một bên vừa phát vừa verify. **Không hợp** với
nhiều service verify như ở đây.

### 3.2. Bất đối xứng (RSA — RS256) — **hệ thống đang dùng**

- Một **cặp khóa**: **private key** (chỉ Auth giữ, dùng để **ký**) và **public
  key** (công khai, ai cũng có thể lấy, chỉ dùng để **verify**).
- Public key **không** ký được token → service verify được nhưng **không thể giả
  mạo**. Đây chính là tính chất ta cần cho microservices.

```
   Auth: private key (bí mật)              Mọi service: public key (công khai)
ký:   sign(header.payload, privateKey) → sig
verify: verify(header.payload, sig, publicKey) == OK ?
```

| Ưu | Nhược |
|----|-------|
| **Chỉ Auth phát được** token; service khác chỉ verify | Token + khóa lớn hơn, ký/verify nặng hơn HMAC chút |
| Public key phát tán tự do (qua JWKS), lộ cũng không sao | Cần hạ tầng công bố & rotate khóa (JWKS giải quyết) |
| Rotation gọn: đổi cặp khóa ở Auth, service tự lấy public key mới qua JWKS | |

#### Bằng chứng trong code

**Phát token — ký bằng private key, RS256** (`auth/.../JwtServiceImpl.java`):

```java
String token = Jwts.builder()
        .header().keyId(keys.getKeyId()).and()      // gắn kid vào header
        .issuer(properties.getIssuer())             // iss = "auth"
        .subject(user.getId().toString())           // sub
        .claim("email", user.getEmail())
        .claim("roles", List.of(user.getRole().name()))
        .issuedAt(...).expiration(...)
        .signWith(keys.getPrivateKey(), Jwts.SIG.RS256)  // ← ký bằng PRIVATE key
        .compact();
```

**Quản lý cặp khóa** (`auth/.../config/JwtKeys.java`):

- Nếu cấu hình `auth.jwt.private-key` + `auth.jwt.public-key` (PEM, nạp từ **K8s
  Secret**) → load cặp khóa cố định.
- Nếu không → **sinh cặp RSA 2048-bit tạm thời** lúc khởi động và **cảnh báo**:
  token reset mỗi lần restart, không dùng được cho nhiều replica. (Đây là tiện
  cho dev; production **phải** cấp khóa qua Secret.)
- `kid` được suy ra từ thumbprint của public key → ổn định theo từng khóa, cho
  phép nhiều khóa cùng tồn tại lúc rotation.

**Công bố public key qua JWKS** (`auth/.../controller/JwksController.java`):

```java
@GetMapping("/internal/jwks")
public Map<String, Object> jwks() {
    return jwtKeys.jwkSet();   // {"keys": [ <public jwk có kid> ]}
}
```

JWKS (JSON Web Key Set) là **endpoint công khai** trả về danh sách public key
dạng JSON. Bên verify tải document này, chọn key theo `kid` trong header token.
Private key **không bao giờ** rời Auth.

---

## 4. Quy trình verify token trong hệ thống

### 4.1. Hai tầng verify

Mọi traffic nghiệp vụ đi qua gateway; trong cluster mỗi service lại tự verify một
lần nữa — **không tin header do bên ngoài gắn vào**, danh tính luôn đến từ token
đã xác thực mật mã (xem javadoc `ResourceServerAutoConfiguration`).

```
                 ┌─────── login (POST /api/auth/public/login) ───────┐
                 ▼                                                    │
 ┌────────┐   Bearer JWT    ┌───────────────┐   Bearer JWT   ┌──────────────────┐
 │ Client │ ───────────────▶│  apigateway   │ ──────────────▶│ catalog / order  │
 └────────┘                 │  (WebFlux)    │   (cùng token)  │ /inventory/...   │
                            │ verify JWT #1 │                │  verify JWT #2   │
                            └───────┬───────┘                └────────┬─────────┘
                                    │ tải & cache JWKS                │ tải & cache JWKS
                                    └──────────────┬─────────────────┘
                                                   ▼
                                       ┌────────────────────────┐
                                       │  auth  GET /internal/jwks │  ← chỉ PUBLIC key
                                       └────────────────────────┘
```

> JWKS được client (gateway/service) **cache lại**; Auth **không** bị gọi trên
> đường đi của mỗi request — chỉ khi cần nạp/refresh khóa. Đây là điểm giữ cho hệ
> thống stateless và Auth không thành nút cổ chai.

### 4.2. Các bước verify một request (chi tiết)

Với một request mang `Authorization: Bearer <token>`:

1. **Tách token** khỏi header `Authorization`. Không có Bearer hợp lệ ở endpoint
   `authenticated()` → **401**. (Các path `permitAll()` như `/api/auth/**`,
   actuator health, swagger thì bỏ qua bước này.)
2. **Đọc header token, lấy `kid`.**
3. **Lấy public key tương ứng `kid`** từ JWKS đã cache (nạp từ Auth nếu chưa có).
4. **Verify chữ ký** `header.payload` bằng public key đó (RS256). Sai chữ ký →
   **401**. *(Bước này chặn token giả/sửa.)*
5. **Validate claim chuẩn**: `exp` chưa quá hạn, `nbf`/`iat` hợp lý
   (`JwtValidators.createDefault()`).
6. **Validate `iss`** = `auth` (`JwtIssuerValidator`) — chỉ chấp nhận token do
   đúng Auth phát.
7. **Map quyền**: claim `roles` (vd `["USER"]`) → authority `ROLE_USER`
   (`JwtGrantedAuthoritiesConverter` + prefix `ROLE_`).
8. Đặt `Authentication` vào SecurityContext → controller/`@PreAuthorize` dùng để
   phân quyền. Thiếu quyền → **403**.

### 4.3. Cấu hình verify ở từng nơi

**Gateway** (`apigateway/.../config/SecurityConfig.java` — *reactive*,
`NimbusReactiveJwtDecoder`): lấy khóa qua **`jwk-set-uri`**.

```properties
# apigateway/src/main/resources/application.properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${JWK_SET_URI:http://localhost:8081/internal/jwks}
security.jwt.issuer=${JWT_ISSUER:auth}
```

```java
NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefault(), new JwtIssuerValidator(issuer)));   // default + iss
```

**Các service nghiệp vụ** (Catalog, Order, Inventory, Payment, Ticket…) — qua
module dùng chung `common-security` (`ResourceServerAutoConfiguration`, *servlet*,
`NimbusJwtDecoder.withJwkSetUri`). Mỗi service chỉ cần:

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://auth:8081/internal/jwks
app.security.issuer=auth
# tùy chọn path public riêng của service:
# app.security.public-paths=/api/catalog/public/**
```

**Auth tự verify token của chính mình** (`auth/.../config/SecurityConfig.java`):
khác biệt nhỏ — Auth dùng **public key cục bộ** (`NimbusJwtDecoder.withPublicKey`)
lấy thẳng từ `JwtKeys`, không cần tự gọi JWKS của mình.

### 4.4. Bảng tổng hợp decoder theo nơi

| Nơi verify | Loại app | Decoder | Nguồn public key | Validate |
|------------|----------|---------|------------------|----------|
| `apigateway` | Reactive (Netty) | `NimbusReactiveJwtDecoder` | JWKS URI (cache) | default + `iss=auth` |
| Catalog/Order/Inventory/Payment/Ticket | Servlet | `NimbusJwtDecoder` (qua `common-security`) | JWKS URI (cache) | default + `iss` |
| `auth` (tự verify) | Servlet | `NimbusJwtDecoder.withPublicKey` | Public key cục bộ trong `JwtKeys` | default + `iss` |

---

## 5. Điểm cần nhớ

- **Stateless**: server không lưu phiên; token tự mang danh tính, verify cục bộ.
  Đổi lại: chưa revoke được trước hạn (token sống tối đa TTL = 1 giờ).
- **Bất đối xứng RS256**: chỉ Auth (giữ private key) phát được token; mọi service
  chỉ verify bằng public key → gọi thẳng service trong cluster cũng **không giả
  mạo** được danh tính.
- **JWKS** là kênh phân phối public key; private key không bao giờ rời Auth.
- **Hai tầng verify** (gateway + service) đều **không gọi Auth mỗi request** —
  giữ Auth khỏi thành nút cổ chai, hợp ràng buộc flash sale + on-prem không autoscale.
- **Production phải** cấp cặp khóa qua K8s Secret (`auth.jwt.private-key/public-key`);
  khóa sinh tạm khi khởi động chỉ dùng cho dev một-replica.
