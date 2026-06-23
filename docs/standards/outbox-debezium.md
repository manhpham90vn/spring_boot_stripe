# Transactional Outbox + Debezium — phát event không mất, và cách mở rộng

> **Mục đích:** giải thích cơ chế phát event giữa các service (outbox + CDC) đang chạy
> trong repo, và **quy ước để thêm event/producer/consumer mới**. Bám sát code thật:
> `auth/` (producer mẫu), `notification/` (consumer mẫu), `infra/debezium/`,
> `docker-compose.yml`. Ràng buộc gốc: [`/CLAUDE.md`](../../CLAUDE.md); tổng quan kiến trúc:
> [`architecture.md`](../overview/architecture.md).
>
> **TL;DR:** Service ghi event vào **bảng `outbox` cùng transaction nghiệp vụ** (không
> publish trực tiếp). **Debezium** đọc INSERT từ **WAL của Postgres** (CDC) rồi đẩy lên
> **Kafka** topic `<aggregateType>.events`. Consumer (Spring Cloud Stream) tiêu thụ. Nhờ
> vậy event và thay đổi nghiệp vụ **không bao giờ lệch nhau**, và event **không mất**.

---

## 1. Vì sao cần outbox — bài toán "dual write"

Nếu trong một request ta vừa ghi DB vừa gọi Kafka trực tiếp:

```
save(user)        ✅ commit DB
kafka.send(event) ❌ Kafka lỗi / app chết giữa chừng
```

→ DB đã có user nhưng event "đã đăng ký" **mất** → Notification không gửi mail, dữ liệu
hai bên lệch nhau. Đây là **dual-write problem**: hai hệ thống không thể commit nguyên tử.

**Outbox** giải bài toán bằng cách biến "phát event" thành **một dòng ghi DB cùng
transaction**. Chỉ còn MỘT lần commit nguyên tử (user + outbox row). Việc đưa event ra
Kafka tách thành bước sau, do Debezium đọc lại từ WAL — không bao giờ bỏ sót.

---

## 2. Luồng tổng thể

```
        ┌─────────────────────── auth service ───────────────────────┐
 POST   │  @Transactional register():                                 │
 /register  │    users.save(user)            ┐  CÙNG 1 transaction       │
 ───────►│    outbox.fire(UserRegistered) ┘  → commit nguyên tử       │
        │         (ghi 1 dòng vào bảng outbox, KHÔNG gọi Kafka)        │
        └───────────────────────────┬─────────────────────────────────┘
                                    │ INSERT vào WAL (write-ahead log)
                                    ▼
        ┌──────────── Debezium (Kafka Connect, container "connect") ──┐
        │  PostgresConnector đọc WAL qua replication slot             │
        │  + SMT EventRouter: route theo aggregate_type,              │
        │    expand payload JSON, gắn header eventType                │
        └───────────────────────────┬─────────────────────────────────┘
                                    │ produce
                                    ▼
                        Kafka topic  user.events   (key = aggregate_id)
                                    │
                                    ▼
        ┌──────────── notification service (Spring Cloud Stream) ─────┐
        │  Consumer<UserRegisteredEvent> userRegisteredSink           │
        │    → gửi mail qua SMTP (Mailpit/dev)                         │
        └─────────────────────────────────────────────────────────────┘
```

Điểm mấu chốt: **app KHÔNG nói chuyện với Kafka ở bước ghi**. App chỉ ghi DB; Debezium là
thành phần DUY NHẤT đẩy event đi, dựa trên WAL (nguồn sự thật).

---

## 3. Phía PRODUCER (mẫu: `auth`)

### 3.1 Bảng `outbox` (migration `auth/.../db/migration/V1__init.sql`)

```sql
CREATE TABLE outbox (
    id             UUID        PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,   -- "user"  → tên topic
    aggregate_id   VARCHAR(64) NOT NULL,   -- id user → message key (giữ thứ tự)
    event_type     VARCHAR(64) NOT NULL,   -- "UserRegistered" → header eventType
    payload        TEXT        NOT NULL,    -- JSON dữ liệu nghiệp vụ
    created_at     TIMESTAMPTZ NOT NULL     -- CHỈ để dọn rác (xem §6 cạm bẫy)
);
```
Tên cột khớp field mapping của Debezium Outbox Event Router (§4).

### 3.2 Mô hình event trong code

| Lớp | Vai trò |
|-----|---------|
| `common-core` › `common.core.dto.OutboxEvent<T>` | **DÙNG CHUNG** (lib `libs/common-core`): lớp cơ sở payload + 3 metadata trừu tượng (`aggregateType`, `aggregateId`, `eventType`) |
| `common-core` › `common.core.event.UserRegisteredEvent` (record) | **DÙNG CHUNG**: **Payload** nghiệp vụ — đúng phần JSON, là CONTRACT chung producer↔consumer. Chỉ field cần, KHÔNG secret |
| `event/UserRegisteredOutboxEvent` | (PER-SERVICE, ở producer) Bọc payload + gắn metadata định tuyến cụ thể |
| `handle/OutboxEventSender` | Cây cầu DUY NHẤT: serialize payload → JSON, `repository.save(...)` |
| `entities/OutboxEventEntity` | Map tới bảng `outbox` |
| `scheduler/OutboxPurgeJob` | Dọn dòng cũ (chỉ housekeeping) |

### 3.3 Ghi event — trong transaction nghiệp vụ

```java
@Transactional                              // 1 transaction cho cả hai
public User register(RegisterRequest req) {
    User saved = users.saveAndFlush(user);
    outbox.fire(UserRegisteredOutboxEvent.of(
            UserRegisteredEvent.of(saved.getId(), saved.getEmail())));  // chỉ ghi bảng outbox
    return saved;
}
```
`outbox.fire(...)` **không** đẩy Kafka — chỉ INSERT một dòng. Commit transaction = user +
outbox row cùng vào WAL nguyên tử.

---

## 4. Debezium connector (`infra/debezium/auth-outbox-connector.json`)

Đăng ký tự động bởi service `connect-init` trong compose (POST file JSON này lên Kafka
Connect REST). Postgres phải bật logical replication — xem `docker-compose.yml` lệnh
postgres: `wal_level=logical`, `max_replication_slots`, `max_wal_senders`.

Giải thích các field quan trọng:

```jsonc
"connector.class": "io.debezium.connector.postgresql.PostgresConnector",
"database.dbname": "auth",            // DB của service producer
"plugin.name": "pgoutput",
"slot.name": "auth_outbox_slot",       // slot RIÊNG mỗi connector (đừng trùng)
"publication.name": "auth_outbox_pub", // publication RIÊNG mỗi connector
"table.include.list": "public.outbox", // chỉ theo dõi bảng outbox
"snapshot.mode": "never",              // outbox khởi đầu rỗng → bỏ snapshot

// --- SMT: Outbox Event Router ---
"transforms": "outbox",
"transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
"transforms.outbox.table.field.event.id":      "id",            // dùng chống trùng
"transforms.outbox.table.field.event.key":     "aggregate_id",  // → Kafka message KEY
"transforms.outbox.table.field.event.payload": "payload",       // cột chứa JSON
"transforms.outbox.table.expand.json.payload": "true",          // bung JSON thành value có cấu trúc
"transforms.outbox.route.by.field":            "aggregate_type",
"transforms.outbox.route.topic.replacement":   "${routedByValue}.events", // → "user.events"
"transforms.outbox.table.fields.additional.placement": "event_type:header:eventType"
```

- **Topic** = `<aggregate_type>.events` → `user` ⇒ `user.events`.
- **Key** = `aggregate_id` → mọi event của cùng một aggregate vào cùng partition ⇒ **giữ
  đúng thứ tự** theo aggregate.
- **Header** `eventType` để consumer phân nhánh nếu một topic chở nhiều loại event.
- ⚠️ **KHÔNG** map `created_at` làm `table.field.event.timestamp`: trường đó đòi kiểu
  `INT64` (epoch millis), còn `created_at` là `TIMESTAMPTZ` → SMT văng lỗi, **task chết,
  không event nào lên Kafka**. (Đây là lỗi thật đã gặp — xem §6.)

---

## 5. Phía CONSUMER (mẫu: `notification`)

Spring Cloud Stream kiểu functional — chỉ cần một bean `Consumer`:

```java
@Component
public class UserRegisteredSink implements Consumer<UserRegisteredEvent> {
    public void accept(UserRegisteredEvent event) { /* gửi mail */ }
}
```

Đấu nối qua `application.properties`:
```properties
spring.cloud.function.definition=userRegisteredSink
spring.cloud.stream.bindings.userRegisteredSink-in-0.destination=user.events   # tên topic
spring.cloud.stream.default.group=notification                                  # consumer group
spring.cloud.stream.default.consumer.auto-offset-reset=earliest
```
Quy ước binding: `<tên-bean>-in-0` = cổng vào; trỏ `destination` tới topic.

> **Event payload là CONTRACT dùng chung.** Cả producer (`auth`) và consumer (`notification`)
> dùng chung một class `UserRegisteredEvent` ở lib `libs/common-core` (`common.core.event`) —
> một nguồn sự thật cho "hợp đồng JSON". Jackson vẫn khớp theo **tên field**; field thừa được
> bỏ qua. **Đánh đổi:** đổi shape payload là sửa đồng loạt producer + MỌI consumer — đây là
> chủ ý (ưu tiên contract thống nhất hơn là loose-coupling kiểu mỗi service một bản sao).

---

## 6. Cạm bẫy (đọc trước khi mở rộng)

1. **TUYỆT ĐỐI không publish Kafka trực tiếp trong code nghiệp vụ.** Đó là dual-write.
   Luôn đi qua `outbox.fire(...)`.
2. **`table.field.event.timestamp` phải là INT64 hoặc BỎ HẲN.** Map vào cột `TIMESTAMPTZ`
   sẽ làm task connector FAILED ⇒ đứt toàn bộ luồng (đăng ký không ra mail). Dấu hiệu:
   `DataException: Field 'created_at' is not of type INT64`.
3. **Consumer phải IDEMPOTENT.** Debezium giao **at-least-once** → một event có thể tới
   >1 lần (vd connector restart). Xử lý lại không được gây hại (vd dùng `event id` / khóa
   nghiệp vụ để chống trùng), nhất là với phát vé / trừ tồn / thu tiền.
4. **`created_at` chỉ để dọn rác**, KHÔNG phải nguồn phát. `OutboxPurgeJob` xóa dòng cũ là
   an toàn vì WAL (qua replication slot) mới là nguồn sự thật; slot giữ WAL tới khi Debezium
   đọc xong.
5. **Slot giữ WAL → coi chừng phình.** Nếu connector chết lâu, WAL không được giải phóng.
   Đã chặn trần bằng `max_slot_wal_keep_size` (compose). Theo dõi trạng thái connector.
6. **Mỗi producer service = một connector riêng**, `slot.name`/`publication.name` **không
   được trùng** giữa các service.

---

## 7. MỞ RỘNG — thêm một event mới

Ví dụ: `order` phát `OrderCompleted` cho `ticket` (phát vé) + `notification` (gửi vé).

### 7.1 Ở service PRODUCER (`order`)
1. **Bảng outbox**: thêm `CREATE TABLE outbox (...)` y hệt §3.1 vào migration của `order`.
2. **Bộ khung outbox**: tạo `OutboxEventEntity`, `OutboxEventSender`, `OutboxEventRepository`,
   `OutboxPurgeJob` (copy khuôn từ `auth`). `OutboxEvent<T>` base **lấy từ `common-core`**
   (`common.core.dto`) — không copy per-service nữa; thêm dependency `common-core` nếu chưa có.
3. **Event**: thêm record payload `OrderCompletedEvent` vào `libs/common-core`
   (`common.core.event`) + `OrderCompletedOutboxEvent` (per-service, ở producer) với:
   - `aggregateType = "order"` → topic `order.events`
   - `aggregateId = orderId` → key/giữ thứ tự theo đơn
   - `eventType = "OrderCompleted"`
4. **Phát**: gọi `outbox.fire(...)` **bên trong** `@Transactional` của saga (vd khi chốt đơn).
5. **Connector**: thả `infra/debezium/order-outbox-connector.json` (đổi `database.dbname=order`,
   `slot.name=order_outbox_slot`, `publication.name=order_outbox_pub`, `topic.prefix=order`).
   `connect-init` **tự đăng ký mọi file** trong thư mục — chỉ cần thêm một dòng
   `order: { condition: service_healthy }` vào `depends_on` của `connect-init` (xem §7.3).

### 7.2 Ở service CONSUMER (`ticket`, `notification`)
1. Thêm bean `Consumer<OrderCompletedEvent> orderCompletedSink`.
2. `application.properties`:
   ```properties
   # nhiều consumer: nối bằng dấu ;
   spring.cloud.function.definition=...;orderCompletedSink
   spring.cloud.stream.bindings.orderCompletedSink-in-0.destination=order.events
   ```
3. Dùng chung record `OrderCompletedEvent` từ `common-core` (thêm dependency `common-core`
   nếu service chưa có) — KHÔNG tạo bản sao per-service nữa.
4. **Idempotent**: vd `ticket` kiểm "đã phát vé cho orderId này chưa" trước khi phát.

### 7.3 Khi có NHIỀU service producer — cấu hình Debezium

**Quy tắc cốt lõi: một connector cho MỖI service.** Một Debezium Postgres connector chỉ
gắn được **một database**; vì database-per-service nên KHÔNG thể dùng chung — mỗi service
phát event cần connector riêng trỏ vào bảng `outbox` của DB nó.

Mỗi connector chỉ khác nhau ~5 field, phần còn lại (`transforms.outbox.*`, converter,
`table.include.list=public.outbox`, `snapshot.mode=never`) **giống hệt** — copy file đổi 5 dòng:

| Field | auth | order | payment |
|------|------|-------|---------|
| `name` | `auth-outbox-connector` | `order-outbox-connector` | `payment-outbox-connector` |
| `database.dbname` | `auth` | `order` | `payment` |
| `slot.name` | `auth_outbox_slot` | `order_outbox_slot` | `payment_outbox_slot` |
| `publication.name` | `auth_outbox_pub` | `order_outbox_pub` | `payment_outbox_pub` |
| `topic.prefix` | `auth` | `order` | `payment` |

> ⚠️ `slot.name` và `publication.name` **bắt buộc duy nhất**. Hai connector trùng slot trên
> cùng DB sẽ giẫm chân nhau, hỏng cả hai.

**Đăng ký connector:**
- **Dev (compose):** `connect-init` lặp đăng ký mọi `infra/debezium/*.json` (idempotent:
  201 tạo mới / 409 đã có). Thêm producer = thả file JSON + thêm `<svc>: service_healthy`
  vào `depends_on` của `connect-init` (để bảng `outbox` tồn tại trước khi tạo publication).
- **Prod (K8s/Strimzi):** một `KafkaConnect` cluster (`deploy/infra/kafka/connect.yaml`) +
  mỗi service một **`KafkaConnector` CRD** trong `deploy/infra/kafka/connectors/<svc>-outbox-connector.yaml`;
  Strimzi operator tự reconcile — khai báo, GitOps, không POST tay. DB user/pass đọc từ secret
  `ticketing-db-app` qua DirectoryConfigProvider (không hardcode như JSON dev).

**Hạ tầng dùng chung & giới hạn:**
- **Một Kafka Connect cluster chạy N connector.** Mỗi outbox connector `tasks.max=1` (1 bảng)
  → N service = N task; scale bằng tăng **worker replicas** của Connect, task tự rebalance.
- **Postgres slot/walsender:** mỗi connector giữ 1 slot + 1 walsender trên DB của nó.
  - *Dev* (một instance nhiều DB): ngân sách theo **instance** → `max_replication_slots` /
    `max_wal_senders` (hiện **10/10** trong compose) phải ≥ số connector; >10 service thì nâng.
  - *Prod* (**1 cluster CloudNativePG dùng chung**, 7 database — không phải cluster-per-service):
    slot/walsender vẫn theo **instance** như dev → `max_replication_slots`/`max_wal_senders` đặt
    **10/10** trong `deploy/infra/postgres/cluster.yaml` phải ≥ số connector; >10 producer thì nâng.
- **Topic:** dev auto-create; prod nên **pre-create** `<aggregateType>.events` với số
  partition (ordering theo key) + replication factor mong muốn.

---

## 8. Quy ước đặt tên (tóm tắt)

| Thứ | Quy ước | Ví dụ |
|-----|---------|-------|
| `aggregateType` | danh từ thường, số ít | `user`, `order`, `payment` |
| Topic Kafka | `<aggregateType>.events` | `user.events`, `order.events` |
| `aggregateId` | id của aggregate (→ message key) | `userId`, `orderId` |
| `eventType` | PascalCase, thì quá khứ | `UserRegistered`, `OrderCompleted` |
| Consumer bean / binding | `<tên>Sink` / `<tên>Sink-in-0` | `userRegisteredSink-in-0` |
| Debezium slot/publication | `<service>_outbox_slot` / `_pub` | `auth_outbox_slot` |

---

## 9. Vận hành & debug nhanh

```bash
# Trạng thái connector (task FAILED là đứt luồng)
curl -s http://localhost:8085/connectors/auth-outbox-connector/status | jq .

# Liệt kê topic + có message chưa
docker exec ticketing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Còn dòng outbox tồn chưa phát?
docker exec ticketing-postgres psql -U app -d auth -c "SELECT count(*) FROM outbox;"

# Consumer đã xử lý? (notification)
docker logs ticketing-notification 2>&1 | grep -i 'welcome\|error'

# Mail vào chưa (dev = Mailpit)
curl -s http://localhost:8025/api/v1/messages | jq '.total'   # hoặc mở http://localhost:8025
```

> Cập nhật config connector đang chạy mà không xóa: `PUT .../connectors/<name>/config` với
> phần `.config`. Vì offset chưa commit khi task lỗi, các record tồn đọng sẽ được phát lại
> sau khi sửa.

---

## 10. Liên quan
- [`architecture.md`](../overview/architecture.md) — bản đồ service & ràng buộc.
- [`API-CONVENTIONS.md`](API-CONVENTIONS.md) — quy ước path (event là kênh khác, async).
- [`dev-runbook.md §4`](../overview/dev-runbook.md) — smoke-test HTTP đồng bộ (curl). Luồng mail
  async kiểm thủ công qua Mailpit (http://localhost:8025) sau khi đăng ký.
