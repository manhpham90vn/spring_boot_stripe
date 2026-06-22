# Dev Runbook — chạy stack local & kiểm tra

> **Mục đích:** từ 0 dựng được toàn bộ hệ thống trên máy (docker-compose), biết cổng/công
> cụ, chạy smoke-test, và debug các sự cố thường gặp. Bám sát `docker-compose.yml` thật.
> Ràng buộc kiến trúc: [`/CLAUDE.md`](../../CLAUDE.md); tổng quan: [`architecture.md`](architecture.md).

---

## 1. Yêu cầu
- Docker + Docker Compose v2 (`docker compose ...`).
- Cổng trống: 80, 1025, 5432, 5540, 6379, 8025, 8080–8092, 9000–9001, 9092, và 5173–5174
  (FE) (xem §3).
- Lần đầu build image dev + tải dependency Maven → **chậm** (mỗi service `start_period: 120s`
  vì compile khi khởi động). Kiên nhẫn ở lần `up` đầu tiên.

## 2. Khởi động

Compose chia theo **profile** (không có profile nào mặc định → `docker compose up` trơ
trọi sẽ không dựng gì). Bật đúng nhóm cần:

| Profile | File | Nội dung |
|---------|------|----------|
| `db` | `docker-compose.yml` | hạ tầng lưu trữ: postgres, redis, kafka, minio(+init), mailpit |
| `services` | `docker-compose.yml` | init containers + Debezium connect + 9 service + nginx (**tự kéo `db` lên cùng**) |
| `frontend` | `docker-compose.yml` | web (React :5173) + admin (Vue :5174), vite dev |
| `tunnel` | `docker-compose.yml` | cloudflared (chạy kèm `services`) |
| `tools` | `docker-compose.tools.yml` | UI quản lý: pgweb, RedisInsight, kafka-ui, swagger-ui |

```bash
docker compose --profile services up -d                       # backend đầy đủ (kèm hạ tầng)
docker compose --profile db up -d                             # chỉ hạ tầng
docker compose --profile services --profile frontend up -d    # backend + FE
docker compose ps                                             # xem trạng thái (chờ healthy)
docker compose logs -f auth                                   # theo dõi log một service

# Tools quản lý (join network của stack chính → bật SAU khi stack đã chạy):
docker compose -f docker-compose.tools.yml --profile tools up -d
```

**Thứ tự init tự động** (các container one-shot, `restart: "no"`):
1. `jwt-keys-init` — sinh cặp khoá RSA vào `infra/keys/` (nếu chưa có) cho Auth ký JWT.
2. `common-security-init` — `mvn install` module `libs/common-security` vào maven-cache để
   các service servlet dùng được.
3. `common-core-init` — `mvn install` module `libs/common-core` (model dùng chung:
   `OutboxEvent`, `ApiError`, event payload) vào maven-cache cho các service phụ thuộc.
4. `postgres` init — tạo các DB `auth, catalog, inventory, order, payment, ticket,
   notification` (`infra/postgres/init/01-create-databases.sql`).
5. `minio-init` — tạo bucket `event-images`, `ticket-qr`.
6. `connect-init` — đăng ký **mọi** connector Debezium trong `infra/debezium/*.json`
   (xem [`outbox-debezium.md`](../standards/outbox-debezium.md)).

> **Postgres bật logical replication** (`wal_level=logical`, slots) cho Debezium — đã cấu
> hình sẵn trong lệnh của service `postgres`.

## 3. Bản đồ cổng & công cụ

| Thành phần | URL / cổng | Ghi chú |
|-----------|-----------|---------|
| **nginx (edge)** | http://localhost | Cửa vào chính: `/api/**`, `/webhooks/**`, `/healthz`, `/health/<svc>`, `/swagger/` |
| apigateway | :8080 | Gateway nghiệp vụ (thường gọi qua nginx) |
| auth | :8081 | `/internal/jwks` (chỉ nội bộ) |
| catalog | :8082 | |
| inventory · order | :8083 · :8084 | luồng mua chạy e2e |
| payment · ticket | :8086 · :8087 | payment: chuyển sang Stripe thật (bỏ mock) |
| notification · waitingroom | :8088 · :8089 | notification: consumer Kafka + DB dedup |
| **web (storefront)** | http://localhost:5173 | React + vite dev (profile `frontend`); proxy `/api` → apigateway |
| **admin** | http://localhost:5174 | Vue + vite dev (profile `frontend`); proxy `/api` → apigateway |
| **Mailpit** (xem mail dev) | http://localhost:8025 | SMTP nhận ở :1025 |
| **MinIO console** | http://localhost:9001 | user/pass: `minioadmin`/`minioadmin`; API :9000 |
| **Kafka Connect REST** | http://localhost:8085 | trạng thái Debezium connector |
| **cloudflared (tunnel)** | profile `tunnel` | Public URL HTTPS cho Stripe webhook → nginx → payment |
| PostgreSQL | :5432 | user/pass/db: `app`/`app` (mỗi service một DB) |
| Redis | :6379 | |
| Kafka | :9092 | |

**Tools quản lý** (`docker-compose.tools.yml`, profile `tools`):

| Công cụ | URL | Ghi chú |
|---------|-----|---------|
| **Swagger UI** | http://localhost:8092/swagger | Gom OpenAPI mọi service; cũng vào được qua nginx http://localhost/swagger |
| **pgweb** (Postgres) | http://localhost:8091 | Chế độ sessions: tự nhập DB — host `postgres`, port `5432`, user/pass `app`/`app` |
| **RedisInsight** | http://localhost:5540 | Thêm host `redis:6379` |
| **kafka-ui** | http://localhost:8090 | Topic + Debezium connect (cluster `ticketing`) |

### Webhook Stripe vào máy dev — 2 cách
- **`stripe listen`** (nhanh, không cần public URL): `stripe listen --forward-to localhost/webhooks/stripe`
  → in ra `whsec_...`; đặt `STRIPE_WEBHOOK_SECRET` rồi restart payment.
- **Cloud tunnel** (để Stripe Dashboard gọi vào URL thật):
  ```bash
  docker compose --profile tunnel up -d cloudflared
  docker compose logs -f cloudflared        # đọc https://<random>.trycloudflare.com
  ```
  Dán `https://<random>.trycloudflare.com/webhooks/stripe` vào Stripe Dashboard (endpoint),
  lấy `whsec_...` của endpoint đó → đặt `STRIPE_WEBHOOK_SECRET`. URL quick tunnel **đổi mỗi
  lần khởi động**; muốn cố định → named tunnel (`CLOUDFLARE_TUNNEL_TOKEN`, xem comment compose).
  Tunnel trỏ vào **nginx** (DMZ) nên chỉ lộ `/webhooks`, `/api`, `/health`, `/swagger` — KHÔNG lộ `/internal`.

## 4. Kiểm tra "có gãy gì không" (smoke)
Chạy nhanh bằng `curl` qua nginx (edge). **Smoke health + routing:**
```bash
curl -s http://localhost/healthz                       # edge sống
for s in auth catalog inventory order payment ticket notification waitingroom; do
  curl -s -o /dev/null -w "%{http_code}  $s\n" http://localhost/health/$s
done
```
**Ma trận quyền** (mong đợi: public `200`, thiếu token `401`, `/internal` không route qua edge `404`):
```bash
curl -s -o /dev/null -w "%{http_code}  public events (→200)\n"   http://localhost/api/catalog/public/events
curl -s -o /dev/null -w "%{http_code}  /api/auth/me (→401)\n"    http://localhost/api/auth/me
curl -s -o /dev/null -w "%{http_code}  /internal/jwks (→404)\n"  http://localhost/internal/jwks
```
**Luồng mua vé (saga):** cần ADMIN seed danh mục (§5) và `STRIPE_API_KEY` thật ở payment
(dummy → đơn `PAYMENT_FAILED`); xác nhận thẻ qua Payment Element, kết quả chốt từ webhook
(`stripe listen --forward-to localhost:8086/webhooks/stripe`).

## 5. Tạo tài khoản ADMIN (seed)
Đăng ký luôn ra role USER. Thăng quyền qua DB:
```bash
# 1) đăng ký user qua API
curl -s -X POST http://localhost/api/auth/public/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@local","password":"Admin1234!"}'
# 2) thăng ADMIN trong DB auth
docker exec ticketing-postgres psql -U app -d auth \
  -c "UPDATE users SET role='ADMIN' WHERE email='admin@local';"
```

## 6. Thao tác thường dùng
```bash
# Đăng nhập lấy token
curl -s -X POST http://localhost/api/auth/public/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@local","password":"Admin1234!"}'

# Gọi API cần token
TOKEN=...; curl -s http://localhost/api/auth/me -H "Authorization: Bearer $TOKEN"

# Duyệt danh mục (công khai, không token)
curl -s http://localhost/api/catalog/public/events

# Xem mail chào mừng: mở http://localhost:8025
```

## 7. Debug nhanh khi có sự cố

| Triệu chứng | Soi ở đâu |
|-------------|-----------|
| Service không UP | `docker compose logs <svc>`; nhớ first-boot ~120s |
| Đăng ký xong **không thấy mail** | Debezium connector FAILED → `curl -s localhost:8085/connectors/auth-outbox-connector/status \| jq` (xem [`outbox-debezium.md §9`](../standards/outbox-debezium.md)) |
| Event không tới consumer | `docker exec ticketing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`; log notification |
| Outbox tồn đọng | `docker exec ticketing-postgres psql -U app -d auth -c "SELECT count(*) FROM outbox;"` |
| 401/403 bất ngờ | đối chiếu [`SECURITY-ACCESS-CONTROL.md`](../standards/SECURITY-ACCESS-CONTROL.md) + [`API-CONVENTIONS.md`](../standards/API-CONVENTIONS.md) |
| Cần verify JWT thủ công | JWKS chỉ nội bộ: `curl -s http://localhost:8081/internal/jwks` |

## 8. Dọn dẹp
```bash
docker compose down            # dừng stack chính, giữ volume (DB/Redis/Kafka còn dữ liệu)
docker compose down -v         # xoá sạch cả volume (reset hoàn toàn)
docker compose -f docker-compose.tools.yml down   # dừng nhóm tools (file riêng)
```
> `docker compose down` xoá mọi container của project bất kể profile. Tools nằm ở project
> riêng (`ticketing-tools`) nên phải `down` bằng file của nó; network `ticketing-net` chỉ
> biến mất khi stack chính `down`.
> Xoá volume sẽ mất DB + offset Kafka + slot. Lần `up` sau, `connect-init` đăng ký lại
> connector; outbox bắt đầu lại từ rỗng (`snapshot.mode=never`).

## 9. Liên quan
- [`outbox-debezium.md`](../standards/outbox-debezium.md) — luồng event async & debug connector.
- [`SECURITY-ACCESS-CONTROL.md`](../standards/SECURITY-ACCESS-CONTROL.md) · [`API-CONVENTIONS.md`](../standards/API-CONVENTIONS.md) — vì sao 401/403, path nào public.
