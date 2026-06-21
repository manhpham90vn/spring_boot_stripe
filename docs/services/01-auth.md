# 1. Auth/User service (`auth/`)

## Trách nhiệm
Đăng ký, đăng nhập, phát **JWT** (ký RS256 bằng private key; gateway verify bằng
public key). Sở hữu user, credential, role. Phát `UserRegistered` cho Notification.

## Database `auth`

### users
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| email | VARCHAR(320) | NOT NULL, UNIQUE (`ux_users_email`) |
| password_hash | VARCHAR(100) | NOT NULL — BCrypt |
| role | VARCHAR(32) | NOT NULL — `USER` \| `ADMIN` |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### outbox  (khuôn chuẩn — phát `UserRegistered`)
`id UUID PK, aggregate_type, aggregate_id, event_type, payload TEXT, created_at`,
index `ix_outbox_created_at`.

## API
| Method | Path | Auth | Request | Response | Lỗi |
|---|---|---|---|---|---|
| POST | `/api/auth/public/register` | – | `{email, password}` | 201 `{id,email,role}` | 409 trùng email · 400 sai định dạng/mật khẩu ngắn |
| POST | `/api/auth/public/login` | – | `{email, password}` | 200 `{accessToken, tokenType:"Bearer", expiresIn}` | **401** sai mật khẩu / email không tồn tại (giống nhau — chống dò) |
| GET | `/api/auth/me` | JWT | – | 200 `{id, email, role}` | 401 token sai/thiếu |
| GET | `/internal/jwks` | internal | – | 200 JWKS `{keys:[...]}` | – |

### JWT claims
`sub` = userId (UUID), `email`, `role`, `iat`, `exp`. Ký **RS256**; private key nạp
từ K8s Secret, public key công bố qua `/internal/jwks` (gateway cache).

## Event
`UserRegistered {userId, email}` → topic `user.events` (qua outbox) → Notification
gửi welcome (tùy chọn).

## Bảo mật
- Mật khẩu hash **BCrypt** (không lưu plaintext). Policy độ dài tối thiểu validate ở DTO.
- Đăng nhập sai và email không tồn tại trả **cùng** 401 + thông điệp chung.
- `/internal/jwks` chỉ truy cập nội bộ (không route ở gateway).

## Config
- `server.port=8081`, DB `auth`.
- Khóa ký: `auth.jwt.private-key` / `public-key` (PEM) từ Secret; `auth.jwt.ttl`.

## Lưu ý triển khai
- Giữ gọn; chưa cần refresh token/quên mật khẩu trong phạm vi hiện tại (thêm sau khi cần).
- Rate limit route `/api/auth/**` ở gateway (~5 req/s) — login/register dễ bị brute force.
