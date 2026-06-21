# Dev Runbook — chạy stack local & kiểm tra

> **Mục đích:** từ 0 dựng được toàn bộ hệ thống trên máy (docker-compose), biết cổng/công
> cụ, chạy smoke-test, và debug các sự cố thường gặp. Bám sát `docker-compose.yml` thật.
> Ràng buộc kiến trúc: [`/CLAUDE.md`](../../CLAUDE.md); tổng quan: [`architecture.md`](architecture.md).

---

## 1. Yêu cầu
- Docker + Docker Compose v2 (`docker compose ...`).
- Cổng trống: 80, 1025, 5432, 6379, 8025, 8080–8089, 9000–9001, 9092 (xem §3).
- Lần đầu build image dev + tải dependency Maven → **chậm** (mỗi service `start_period: 120s`
  vì compile khi khởi động). Kiên nhẫn ở lần `up` đầu tiên.

## 2. Khởi động
```bash
docker compose up -d          # dựng toàn bộ (hạ tầng + 9 service)
docker compose ps             # xem trạng thái (chờ tất cả healthy)
docker compose logs -f auth   # theo dõi log một service
```

**Thứ tự init tự động** (các container one-shot, `restart: "no"`):
1. `jwt-keys-init` — sinh cặp khoá RSA vào `infra/keys/` (nếu chưa có) cho Auth ký JWT.
2. `common-security-init` — `mvn install` module `common-security` vào maven-cache để
   các service servlet dùng được.
3. `postgres` init — tạo các DB `auth, catalog, inventory, order, payment, ticket`
   (`infra/postgres/init/01-create-databases.sql`).
4. `minio-init` — tạo bucket `event-images`, `ticket-qr`.
5. `connect-init` — đăng ký **mọi** connector Debezium trong `infra/debezium/*.json`
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
| notification · waitingroom | :8088 · :8089 | notification: consumer Kafka · waitingroom: chưa dựng |
| **Swagger UI** | http://localhost/swagger | Gom OpenAPI mọi service |
| **Mailpit** (xem mail dev) | http://localhost:8025 | SMTP nhận ở :1025 |
| **MinIO console** | http://localhost:9001 | user/pass: `minioadmin`/`minioadmin`; API :9000 |
| **Kafka Connect REST** | http://localhost:8085 | trạng thái Debezium connector |
| PostgreSQL | :5432 | user/pass/db: `app`/`app` (mỗi service một DB) |
| Redis | :6379 | |
| Kafka | :9092 | |

## 4. Kiểm tra "có gãy gì không"
Mở `scripts/test.http` trong IDE (JetBrains HTTP Client) → "Run all requests in file",
hoặc chạy CLI:
```bash
ijhttp scripts/test.http
```
- **PHẦN 1 (smoke):** health 9 service + ma trận public/admin/internal + routing 404.
- **PHẦN 2 (mua vé):** saga + edge tiền/vé — cần ADMIN (xem §5); happy-path cần
  `STRIPE_API_KEY` thật ở payment (dummy → đơn `PAYMENT_FAILED`).

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
docker compose down            # dừng, giữ volume (DB/Redis/Kafka còn dữ liệu)
docker compose down -v         # xoá sạch cả volume (reset hoàn toàn)
```
> Xoá volume sẽ mất DB + offset Kafka + slot. Lần `up` sau, `connect-init` đăng ký lại
> connector; outbox bắt đầu lại từ rỗng (`snapshot.mode=never`).

## 9. Liên quan
- [`outbox-debezium.md`](../standards/outbox-debezium.md) — luồng event async & debug connector.
- [`SECURITY-ACCESS-CONTROL.md`](../standards/SECURITY-ACCESS-CONTROL.md) · [`API-CONVENTIONS.md`](../standards/API-CONVENTIONS.md) — vì sao 401/403, path nào public.
- `scripts/test.http` — test API (smoke bảo mật + luồng mua vé) cho IDE/`ijhttp`.
