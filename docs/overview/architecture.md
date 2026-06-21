# Kiến trúc tổng quan — Nền tảng bán vé sự kiện

> **Mục đích của tài liệu:** làm **căn cứ chuẩn** để triển khai các service còn lại.
> Mọi quy ước ở đây đã được hiện thực hoá trong service `auth/` (service mẫu) — khi
> làm service mới, **copy khuôn từ `auth`** rồi điều chỉnh theo trách nhiệm riêng.
> Ràng buộc gốc nằm ở [`/CLAUDE.md`](../../CLAUDE.md); các cạm bẫy thanh toán ở
> [`docs/payment_issue.md`](../payment-ref/payment_issue.md).

---

## 1. Bối cảnh & ràng buộc bất biến

- **Nghiệp vụ:** bán vé concert quy mô lớn, đặc thù **flash sale** — chục nghìn người
  tranh tồn kho hữu hạn cùng lúc, **tuyệt đối không bán trùng**. Thanh toán qua Stripe.
- **Kiến trúc:** microservices, Spring Boot (JVM), triển khai **on-premise** trên
  **Kubernetes thuần tự quản** (không managed cloud).
- **On-prem không autoscale** → phần cứng cố định, sizing cho throughput bền vững;
  Waiting Room hấp thụ spike. Đây là ràng buộc chi phối thiết kế.
- **Database-per-service:** mỗi service một DB PostgreSQL riêng, **không dùng chung**.
- Không có ACID xuyên service → luồng mua vé dùng **Saga (orchestration)** + bù trừ.
- Phát event không mất bằng **transactional outbox + Debezium CDC**.
- Stripe: **một tài khoản duy nhất**, **không** chia tiền nhiều bên (không Connect/Ledger).

---

## 2. Bản đồ service & cổng

| # | Service | Port | DB | Store nóng | Vai trò lõi |
|---|---------|------|----|-----------|-------------|
| 0 | `apigateway` | 8080 | — | Redis | Cửa vào duy nhất, verify JWT, rate limit, circuit breaker. **Không DB, không nghiệp vụ.** |
| 1 | `auth` | 8081 | `auth` | — | Đăng ký/đăng nhập, phát JWT (RS256), publish JWKS. **Service mẫu.** |
| 2 | `catalog` | 8082 | `catalog` | Redis (cache) | Sự kiện, địa điểm, seat map, ticket type, giá. Đọc nhiều ghi ít. |
| 3 | `inventory` | 8083 | `inventory` | Redis | "Còn bao nhiêu, ghế nào trống". Tranh chấp cao nhất. |
| 4 | `order` | 8084 | `order` | — | **Saga orchestrator** luồng mua. Outbox. |
| 5 | `payment` | 8086 | `payment` | — | Cổng **duy nhất** gọi Stripe + webhook. Rate limiter + circuit breaker. |
| 6 | `ticket` | 8087 | `ticket` | — | Vé đã phát, QR ký số. Consume Kafka. |
| 7 | `notification` | 8088 | — | — | Gửi email/SMS. Gần như stateless, consume Kafka. |
| 8 | `waitingroom` | 8089 | — | Redis | Van chống spike (sorted set + CAPTCHA). |

> Cổng 8085 để trống (payment = 8086). Service gọi nhau qua **K8s DNS nội bộ**
> `http://<svc>:<port>` (dev: docker-compose service name, xem `docker-compose.yml`).

```
                    ┌─────────────┐
   Client ───────▶  │  apigateway │  (8080, WebFlux/Netty, JWT verify, rate limit, CB)
                    └──────┬──────┘
          ┌────────┬──────┼───────┬─────────┬───────────┐
          ▼        ▼      ▼       ▼         ▼           ▼
        auth   catalog inventory order   payment   waitingroom
        8081    8082    8083    8084      8086        8089
          │                      │  (Saga orchestrator)
          │                      ├──▶ inventory (giữ chỗ)
          │                      └──▶ payment ──▶ Stripe (1 tài khoản)

   Stripe webhook ──▶ Ingress riêng (DMZ) ──▶ payment  (BỎ QUA apigateway, verify chữ ký Stripe)

   Event async:  service ──(outbox table)──▶ Debezium CDC ──▶ Kafka ──▶ consumer (ticket, notification)
```

---

## 3. Stack & ma trận phiên bản (cố định)

| Thành phần | Phiên bản | Ghi chú |
|-----------|-----------|---------|
| JDK | **21** | `<java.version>21</java.version>` |
| Spring Boot | **4.1.0** | parent `spring-boot-starter-parent` |
| Spring Cloud (gateway/stream/openfeign/circuitbreaker) | **5.0.2** | **pin inline từng dependency** — chưa có Spring Cloud BOM cho Boot 4.1 |
| springdoc-openapi | 3.0.3 | `springdoc-openapi-starter-webmvc-ui` |
| JJWT | 0.13.0 | chỉ auth (và service nào phát/verify token) |
| PostgreSQL | 16 | một instance dev, **mỗi service một database** |
| Redis | 7 | cache/khoá/hàng đợi |
| Kafka | 3.8 (KRaft) | self-hosted |
| Debezium | 2.7 | connector Postgres, Outbox Event Router |

- **`groupId` chuẩn: `com.manhpham`** (base package `com.manhpham.<service>`).
- Boot 4 là **modular autoconfig**: Flyway cần `spring-boot-flyway` (module) **chứ không**
  chỉ `flyway-core`. Xem [memory boot4-modular-autoconfig].
- **Build:** JDK không có sẵn trên PATH host → build qua image dev `ticketing-dev`:
  ```sh
  # Cache .m2 mount đúng HOME của image (user `app`) = /home/app/.m2 — KHÔNG phải /root/.m2.
  docker run --rm --entrypoint sh \
    -v "$PWD/<service>":/workspace -v ticketing-infra_maven-cache:/home/app/.m2 \
    -w /workspace ticketing-dev:latest -c "./mvnw -q -DskipTests test-compile"
  ```
  Service phụ thuộc `common-security` (order, ticket, …): cài nó vào cache trước —
  `... -v "$PWD/common-security":/workspace ... -c "./mvnw -q -DskipTests install"`.

---

## 4. Cấu trúc package chuẩn (mọi service)

Dưới base package `com.manhpham.<service>` — **chỉ tạo thư mục khi có code thật**,
không scaffold rỗng:

```
config/                 @Configuration / @Component hạ tầng (security, JWT keys, beans)
controller/             @RestController (REST nghiệp vụ)
core/dto/               kiểu nền tảng dùng chung (vd OutboxEvent base)
dto/                    record request/response của web
entities/               JPA entity + enum
event/                  DTO của domain event (payload — hợp đồng trên dây)
handle/                 @RestControllerAdvice + helper phát outbox (OutboxEventSender)
processors/sink/        Spring Cloud Stream Consumer<T>  (NHẬN từ Kafka)
processors/source/      Spring Cloud Stream Supplier<T>  (GỬI trực tiếp — hiếm dùng, xem §6)
repositories/jpa/       Spring Data JPA repository
scheduler/              job @Scheduled
services/               interface service
services/impl/          @Service implementation
utils/exception/        exception nghiệp vụ
utils/response/         body response/error chung (ApiError)
feign/client/...        Feign client (chỉ khi thực sự gọi service khác qua Feign)
```

Quy ước: `services` = interface, `services/impl` = impl; `entities` = JPA, `repositories/jpa`
= repo; `dto` = web, `event` = payload event; `handle` = advice. Xem [memory service-package-layout].

---

## 5. Baseline dependency theo loại service

Chỉ thêm cái **thực sự dùng**. Bài học: producer event **không** cần stream-kafka (xem §6).

### 5.1. Service nghiệp vụ có DB (auth, catalog, inventory, order, payment, ticket)
- `spring-boot-starter-web`, `spring-boot-starter-validation`
- `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` (runtime)
- `spring-boot-flyway`, `org.flywaydb:flyway-database-postgresql`
- Observability: `actuator`, `micrometer-registry-prometheus` (runtime),
  `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`
- `org.projectlombok:lombok` (optional), `spring-boot-starter-test` (test)
- `springdoc-openapi-starter-webmvc-ui` (nếu expose REST)
- **Tuỳ nhu cầu:** `spring-boot-starter-security` + JJWT (phát/verify token);
  `spring-boot-starter-data-redis` (catalog/inventory);
  `spring-cloud-starter-stream-kafka` **chỉ khi consume Kafka**.

### 5.2. API Gateway (`apigateway`) — **WebFlux/Netty thuần, TUYỆT ĐỐI KHÔNG JPA/JDBC/DB**
- `spring-cloud-starter-gateway-server-webflux`
- `spring-boot-starter-oauth2-resource-server` (verify JWT bằng JWKS)
- `spring-boot-starter-data-redis-reactive` (backend RequestRateLimiter)
- `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- Observability + test (như trên). Blocking sẽ chẹn event-loop lúc flash sale.

### 5.3. Consumer thuần (`notification`)
- `spring-cloud-starter-stream-kafka` (consume), `spring-boot-starter-mail`
- `spring-boot-starter-web` (chỉ để chạy actuator/swagger — không REST nghiệp vụ)
- Observability + Lombok + test.

---

## 6. Giao tiếp giữa service

### 6.1. Đồng bộ (sync)
- Gọi qua **K8s DNS nội bộ** `http://<svc>:<port>`. Cấu hình URL qua env/ConfigMap.
- Khi cần client khai báo → `spring-cloud-starter-openfeign` (pin 5.0.2), đặt ở
  `feign/client/...`. **Chỉ thêm khi thực sự dùng.**
- Mọi lời gọi ra ngoài phải bọc resilience (timeout, retry backoff, circuit breaker —
  Resilience4j). Đặc biệt payment → Stripe (rate limiter + retry 429).

### 6.2. Bất đồng bộ (async) — **mẫu chuẩn phát/nhận event**

**Bên PHÁT (producer) — dùng Transactional Outbox + Debezium CDC, KHÔNG dùng Spring Cloud Stream:**

1. Trong cùng transaction nghiệp vụ, ghi 1 dòng vào bảng `outbox` qua
   `handle/OutboxEventSender.fire(...)` → atomic với thay đổi nghiệp vụ.
2. **Debezium** (ngoài app) tail WAL của Postgres, route dòng outbox lên Kafka qua
   **Outbox Event Router SMT**. App **không biết gì về Kafka** trên đường phát.
3. Vì publish đi từ WAL nên **không có cột `published_at`/lock**; có job
   `scheduler/OutboxPurgeJob` dọn dòng cũ (housekeeping, không phải publisher).

> ⇒ Producer **không cần** `spring-cloud-starter-stream-kafka` và **không có**
> `processors/source`. (Đây là lý do auth đã gỡ stream-kafka + openfeign.)

Ánh xạ cột outbox → Kafka (Outbox Event Router):

| Cột outbox | Vai trò Kafka |
|-----------|---------------|
| `aggregate_type` | route topic: `<aggregate_type>.events` (vd `user` → `user.events`) |
| `aggregate_id` | message **key** (ordering theo aggregate) |
| `event_type` | header `eventType` (discriminator) |
| `payload` (TEXT JSON) | message **value** (expand JSON) |
| `id` | event id (idempotency phía consumer) |
| `created_at` | **chỉ housekeeping** (để `OutboxPurgeJob` dọn) — KHÔNG map làm event timestamp của router: cột là `TIMESTAMPTZ`, còn `table.field.event.timestamp` đòi `INT64` → map vào sẽ làm task connector FAILED |

Code mẫu (auth): `core/dto/OutboxEvent` (base) → `event/<X>OutboxEvent` (typed wrapper,
khai báo aggregateType/Id/eventType) → `event/<X>Event` (record payload) →
`OutboxEventSender.fire()` → `entities/OutboxEventEntity`/bảng `outbox`.
Connector: `infra/debezium/<svc>-outbox-connector.json` (compose) và
`<svc>/deploy/debezium/<svc>-outbox-connector.yaml` (Strimzi/K8s).

> 📖 **Chi tiết đầy đủ + quy ước mở rộng** (thêm event/producer/consumer mới, cạm bẫy,
> lệnh debug): [`outbox-debezium.md`](../standards/outbox-debezium.md).

**Bên NHẬN (consumer) — dùng Spring Cloud Stream functional binder:**

- Bean `Consumer<T>` đặt ở `processors/sink/`, DTO payload ở `event/` (copy theo
  hợp đồng dây — **giữ tên field khớp producer**).
- Cấu hình:
  ```properties
  spring.cloud.function.definition=<beanName>
  spring.cloud.stream.bindings.<beanName>-in-0.destination=<aggregate_type>.events
  spring.cloud.stream.default.group=<service>
  spring.cloud.stream.default.consumer.auto-offset-reset=earliest
  ```
- Consumer phải **idempotent** (event id) và nên có **DLQ + retry** trước khi tin cậy
  ở production (notification hiện mới nuốt lỗi mail — TODO wire DLQ).

### 6.3. Saga (luồng mua vé)
`order` là **orchestrator**: tạo đơn → gọi inventory giữ chỗ → gọi payment thu tiền →
xác nhận SOLD + phát event sinh vé; lỗi thì chạy **bù trừ** (compensating). Mọi bước
**idempotent xuyên service**.

---

## 7. Dữ liệu & migration

- **Database-per-service.** Dev: một Postgres, mỗi service một DB (`auth`, `catalog`, …),
  URL qua `SPRING_DATASOURCE_URL`.
- **Flyway** sở hữu schema: `src/main/resources/db/migration/V1__init.sql`. **Toàn bộ
  schema đặt trong V1** (sở thích dev hiện tại — xem [memory boot4-modular-autoconfig]).
- `spring.jpa.hibernate.ddl-auto=validate` (Hibernate chỉ validate, không tạo bảng).
- `spring.jpa.open-in-view=false`.
- Postgres phải bật `wal_level=logical` (+ replication slot/publication) cho Debezium —
  đã cấu hình trong `docker-compose.yml`.

---

## 8. Bảo mật

- **JWT verify chỉ ở gateway:** auth phát token RS256, publish public key tại
  `/internal/jwks` (endpoint nội bộ); gateway là OAuth2 resource server, fetch JWKS, verify **chữ ký +
  hạn + issuer cục bộ**, **không gọi auth mỗi request**.
- Claim chuẩn: `sub` (userId), `email`, `roles` (→ `ROLE_*` ở gateway). `iss` phải khớp
  `auth.jwt.issuer`.
- Khoá ký nạp từ **K8s Secret** (`auth.jwt.private-key/public-key` PEM); để trống →
  sinh khoá ephemeral (chỉ dev 1 replica, token reset khi restart).
- Service nghiệp vụ **stateless sau gateway**. Nếu bật Spring Security, nhớ
  `permitAll` cho các actuator cần scrape (xem §9) vì service không có cơ chế auth nội bộ.
- **Webhook Stripe** vào qua Ingress DMZ riêng thẳng tới payment, **bỏ qua gateway**,
  verify **chữ ký Stripe** (không phải JWT), idempotent, đẩy Kafka rồi mới xử lý.

---

## 9. Quan sát (observability) & vận hành

`application.properties` baseline mọi service:

```properties
spring.application.name=<service>
server.port=<port>

management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.tracing.sampling.probability=0.0
management.otlp.tracing.endpoint=${OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}
```

- Prometheus cào `/actuator/prometheus` **trực tiếp** qua DNS nội bộ. Nếu service bật
  Security thì phải `permitAll` cho `/actuator/health/**`, `/actuator/info`,
  `/actuator/prometheus`, `/actuator/metrics/**` (nếu không sẽ luôn 403).
- Tracing: Micrometer Tracing → OTLP. Log: Loki/ELK. Metrics: Prometheus + Grafana.

---

## 10. Xử lý lỗi (chuẩn API)

- `handle/GlobalExceptionHandler` (`@RestControllerAdvice`) ánh xạ exception nghiệp vụ
  → HTTP status + body `utils/response/ApiError` (timestamp, status, error, message,
  fieldErrors).
- Exception nghiệp vụ đặt ở `utils/exception/` (vd `EmailAlreadyUsedException` → 409).
- Validation `MethodArgumentNotValidException` → 400 kèm `fieldErrors`.

---

## 11. Quy ước Lombok

- Entity: **chỉ `@Getter`** — KHÔNG `@Data`/`@Setter`/`@AllArgsConstructor` (tránh
  equals/hashCode sai trên JPA, giữ bất biến qua factory `create(...)`; vẫn để
  constructor `protected` cho JPA).
- Component/service có injection field `final`: `@RequiredArgsConstructor`.
  Nếu constructor có tham số `@Value` → **giữ constructor thủ công** (Lombok không gắn
  annotation lên param sạch).
- Logger: `@Slf4j` thay cho khai báo `Logger` thủ công.
- DTO/event: dùng `record`, không cần Lombok.

---

## 12. Checklist tạo service mới

1. Copy khuôn `pom.xml` từ service cùng loại (§5), đặt `groupId=com.manhpham`,
   `artifactId=<service>`, `java.version=21`. Pin Spring Cloud 5.0.2 inline nếu cần.
2. Base package `com.manhpham.<service>`, dựng cây package theo §4 (chỉ tạo khi có code).
3. `application.properties`: name, port (§2), datasource, `ddl-auto=validate`,
   `open-in-view=false`, block observability (§9).
4. `db/migration/V1__init.sql`: toàn bộ schema (+ bảng `outbox` nếu service phát event).
5. Code theo lát cắt dọc: entity → repo → service(+impl) → controller → handle/advice.
6. **Phát event?** → outbox + `OutboxEventSender` + connector Debezium (§6.2);
   **KHÔNG** thêm stream-kafka. **Nhận event?** → `processors/sink` + stream-kafka.
7. Thêm service vào `docker-compose.yml` (env DB URL, depends_on) và route ở
   `apigateway` (`/api/<path>/**`, rate limiter + circuit breaker).
8. Build kiểm chứng qua image dev (§3) trước khi commit.

---

## 13. Thứ tự triển khai (theo CLAUDE.md)

1. **Phase 0:** hạ tầng dùng chung (K8s + Operator PG/Redis/Kafka, Ingress + MetalLB,
   observability) + API Gateway.
2. **auth (1) + catalog (2)** — mở khoá phần còn lại.
3. **Lát cắt dọc happy path:** inventory (GA) → payment (1 tài khoản) → order. Cột mốc:
   tiền chạy end-to-end.
4. **Đắp thịt:** inventory cho ghế ngồi, ticket + notification chạy async qua Kafka.
5. **waitingroom (8)** — khi đã có luồng hoàn chỉnh để throttle.

---

## 14. Tham chiếu

- [`/CLAUDE.md`](../../CLAUDE.md) — ràng buộc gốc, danh sách service, thứ tự triển khai.
- [`docs/payment_issue.md`](../payment-ref/payment_issue.md) — cạm bẫy thanh toán Stripe (oversell,
  idempotency, webhook, retry, đối soát, đặc thù JPY/Konbini).
- Service mẫu: **`auth/`** — khuôn cho package layout, outbox/CDC, security, lỗi, Lombok.
- `docker-compose.yml` — port, DB, Kafka/Debezium, profiles (`cdc`, `mail`, `full`…).
- `infra/debezium/` + `<svc>/deploy/debezium/` — template connector outbox.
