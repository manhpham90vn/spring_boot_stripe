# Triển khai trên Kubernetes (on-prem) — Ingress, NetworkPolicy, Operator

> **Mục đích:** mô tả cách hệ thống lên K8s thuần tự quản (on-prem), và cung cấp **manifest
> mẫu** cho các POLICY thuộc về ứng dụng (Ingress, NetworkPolicy) — phần mà repo nên giữ dù
> hạ tầng nền (Operator/Helm) là Phase 0 ngoài repo. Ràng buộc gốc: [`/CLAUDE.md`](../CLAUDE.md).
> Liên quan: [`SECURITY-ACCESS-CONTROL.md`](./SECURITY-ACCESS-CONTROL.md),
> [`API-CONVENTIONS.md`](./API-CONVENTIONS.md), [`payment-stripe-flow.md`](./payment-stripe-flow.md).

---

## 1. Ràng buộc nền
- **K8s thuần tự quản**, KHÔNG dùng managed cloud service.
- **On-prem không có cloud LB** → dùng **MetalLB** + **Ingress Controller**.
- **Không autoscale** (HPA tuỳ chọn nhưng phần cứng cố định) → sizing bền vững.
- **Database-per-service**: mỗi service một cụm PostgreSQL riêng.

## 2. Hạ tầng nền (Phase 0 — qua Operator/Helm, ngoài repo)
| Thành phần | Cách dựng |
|-----------|-----------|
| PostgreSQL (mỗi service một cụm) | **CloudNativePG** operator; bật `wal_level=logical` cho Debezium |
| Redis (HA) | Redis operator (Cluster/Sentinel) |
| Kafka + Kafka Connect/Debezium | **Strimzi** operator |
| Vào mạng | **Ingress Controller** + **MetalLB** (cấp IP on-prem) |
| Observability | Prometheus + Grafana + Loki + OpenTelemetry collector |

## 3. Đường vào (Ingress) — 3 lối tách bạch

```
Internet
  │
  ├─ business:  Host/path /api/**  ──▶ Service apigateway:8080   (verify JWT, rate limit)
  │
  ├─ webhook:   path /webhooks/**  ──▶ Service payment:8086      (BỎ QUA gateway = "DMZ")
  │                                     verify chữ ký Stripe, KHÔNG JWT
  │
  └─ (KHÔNG có Ingress cho /internal/** và /actuator,/internal/jwks → không lộ ra ngoài)
```

- **Webhook Stripe phải gọi ngược được** vào endpoint → Ingress rule riêng trỏ thẳng
  Payment (reverse proxy DMZ), **prod bắt buộc TLS** (Stripe chỉ gửi tới HTTPS).
- Nên **giới hạn dải IP Stripe** cho rule webhook + rate-limit ở Ingress.
- KHÔNG tạo Ingress cho `/internal/**` (xem §4) và các path nền (`/actuator`, `/internal/jwks`).

Ví dụ Ingress (rút gọn):
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: business
  annotations: { nginx.ingress.kubernetes.io/limit-rps: "100" }
spec:
  tls: [{ hosts: ["tickets.example.com"], secretName: tickets-tls }]
  rules:
  - host: tickets.example.com
    http:
      paths:
      - { path: /api,      pathType: Prefix, backend: { service: { name: apigateway, port: { number: 8080 } } } }
      - { path: /webhooks, pathType: Prefix, backend: { service: { name: payment,    port: { number: 8086 } } } }
```

## 4. NetworkPolicy — rào THẬT cho `/internal/**`

Quy ước `/internal/**` (service↔service) chỉ an toàn ở prod khi có **NetworkPolicy** chặn
mọi nguồn ngoài cluster/không-được-phép (xem [`SECURITY-ACCESS-CONTROL.md`](./SECURITY-ACCESS-CONTROL.md) §4).
Mô hình: **default-deny ingress** rồi **mở có chọn lọc**.

**(a) Mặc định chặn hết traffic vào mọi pod:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: default-deny-ingress, namespace: ticketing }
spec:
  podSelector: {}            # áp cho MỌI pod
  policyTypes: [Ingress]
  # không có ingress rule = chặn hết
```

**(b) Chỉ cho gateway gọi các service nghiệp vụ (path /api):**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: allow-gateway-to-services, namespace: ticketing }
spec:
  podSelector: { matchLabels: { tier: business } }   # catalog/order/inventory/...
  policyTypes: [Ingress]
  ingress:
  - from: [{ podSelector: { matchLabels: { app: apigateway } } }]
```

**(c) Chỉ cho ORDER gọi INVENTORY/PAYMENT qua /internal:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: allow-order-internal, namespace: ticketing }
spec:
  podSelector: { matchExpressions: [{ key: app, operator: In, values: [inventory, payment] }] }
  policyTypes: [Ingress]
  ingress:
  - from: [{ podSelector: { matchLabels: { app: order } } }]
```

**(d) Cho gateway + service tải JWKS của Auth (`/internal/jwks`):**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: allow-jwks-from-cluster, namespace: ticketing }
spec:
  podSelector: { matchLabels: { app: auth } }
  policyTypes: [Ingress]
  ingress:
  - from: [{ podSelector: { matchLabels: { needs-jwks: "true" } } }]   # gắn label cho gateway + resource servers
```

**(e) Cho Prometheus scrape `/actuator/prometheus`:** mở từ namespace monitoring (rút gọn).

> Kết quả: `/internal/**` chỉ tới được từ caller được cấp phép; từ Internet (qua Ingress)
> không có đường, từ pod lạ trong cluster bị NetworkPolicy chặn.

## 5. Service mesh / mTLS (tuỳ chọn — nâng cấp từ NetworkPolicy)

### 5.1 Ba lớp xác thực, đừng nhầm lẫn
| Cơ chế | Trả lời câu hỏi | Tầng |
|--------|-----------------|------|
| **JWT** (app verify) | "**USER** nào đang gọi?" | L7, danh tính người dùng |
| **mTLS** (mesh) | "Lời gọi này có thật từ **service `order`** không?" | L7, danh tính **workload** + mã hoá |
| **NetworkPolicy** | "Pod A có được **kết nối** tới pod B không?" | L3/L4, chỉ reachability |

→ Ba thứ **bổ sung nhau**, không thay thế. NetworkPolicy chỉ chặn *đường mạng*; mTLS mới
**xác thực danh tính service** + mã hoá traffic in-cluster; JWT vẫn lo danh tính *người dùng*.

### 5.2 Mesh là công nghệ K8s-native
Istio/Linkerd dựa vào **sidecar injection + control plane của K8s** → **docker-compose KHÔNG
chạy mesh đúng nghĩa**. Muốn thử trước khi lên prod: dựng **K8s local nhẹ** (k3d/kind/minikube)
— `k3d` (k3s-in-docker) gần nhất với "K8s thuần on-prem" của dự án — rồi cài mesh để validate
manifest. Compose vẫn dùng cho vòng lặp code nhanh (xem [`dev-runbook.md`](./dev-runbook.md)).

### 5.3 Linkerd vs Istio
| | **Linkerd** | **Istio** |
|--|-------------|-----------|
| mTLS giữa pod | **tự động, gần zero-config** | có (PeerAuthentication) |
| Verify JWT ở sidecar | ❌ không | ✅ `RequestAuthentication` + `AuthorizationPolicy` |
| Độ nặng / vận hành | nhẹ | nặng hơn, nhiều tính năng |
| Hợp khi | chỉ cần **mTLS + identity** in-cluster | muốn **đẩy verify JWT xuống mesh** (hướng B) |

### 5.4 Quan hệ với "hai tầng verify JWT"
(Xem [`SECURITY-ACCESS-CONTROL.md`](./SECURITY-ACCESS-CONTROL.md) — verify JWT ở gateway + service.)

- **Hướng A (mặc định, khuyên dùng):** giữ **JWT verify ở app (zero-trust)** + thêm
  **Linkerd mTLS** cho identity/mã hoá service↔service. Mạnh nhất, ít coupling, không phải
  đổi code. mTLS bổ sung (không thay) cho NetworkPolicy ở `/internal/**`.
- **Hướng B (nâng cao):** dùng **Istio verify JWT tại sidecar** → có thể **bỏ bớt verify
  JWT trong app**. Giảm crypto mỗi hop nhưng **coupling vào mesh** và lệch tinh thần
  zero-trust-ở-app của CLAUDE.md. **Chỉ chọn khi đã có mesh ổn định** và thử kỹ trên k3d.

### 5.5 Lộ trình đề xuất
1. **Phase đầu:** NetworkPolicy (§4) — đủ để chặn `/internal` lộ. Giữ JWT verify ở app.
2. **Khi cần identity/mã hoá in-cluster:** thêm **Linkerd** (mTLS tự động), giữ nguyên JWT.
3. **Chỉ khi có lý do rõ:** cân nhắc **Istio** + chuyển verify JWT xuống mesh (hướng B).

> Tóm lại cho lúc deploy: **bắt buộc** có NetworkPolicy; **nên** thêm Linkerd mTLS cho mạng
> in-cluster; **không** đổi mô hình JWT trừ khi cố ý theo hướng B.

## 6. Cấu hình & secret
- **Config**: ConfigMap cho property không nhạy cảm (route URI, jwk-set-uri nội bộ, issuer).
- **Secret**: cặp khoá JWT của Auth (`auth.jwt.private-key/public-key` hoặc `*-location`),
  `stripe.api-key`, `stripe.webhook-secret`, mật khẩu DB. KHÔNG commit vào repo.
- Service gọi nhau qua **K8s DNS nội bộ** `http://<svc>:<port>` (đã chọn K8s thuần → không Consul).

## 7. Debezium trên K8s (Strimzi)
Mỗi service producer ship một **`KafkaConnector` CRD** (`<svc>/deploy/debezium/<svc>-outbox-connector.yaml`);
Strimzi operator reconcile. Khác dev (compose `connect-init` POST REST). Quy ước slot/
publication/topic.prefix: xem [`outbox-debezium.md`](./outbox-debezium.md) §7.3.

## 8. Lưu ý vận hành
- **TLS** ở Ingress (đặc biệt webhook Stripe).
- **Không autoscale** → đặt resource requests/limits sát sizing; dựa Waiting Room + rate
  limit để chịu spike ([`resilience-flash-sale.md`](./resilience-flash-sale.md)).
- **Probe**: liveness/readiness trỏ `/actuator/health/**` (đã expose, permitAll).
- **Giám sát replication slot** (WAL phình nếu connector chết) + trạng thái connector.
