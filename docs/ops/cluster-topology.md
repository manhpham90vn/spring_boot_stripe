# Kiến trúc cụm Kubernetes — node, pool, lập lịch & mạng

> **Mục đích:** chốt **topology vật lý** của cụm K8s thuần tự quản: cần bao nhiêu node,
> node nào chạy gì, ghim workload thế nào, mạng public/private ra sao, lớp vào (LB/Ingress)
> giải quyết kiểu gì khi thuê VPS cloud (Vultr/EC2) — nơi **MetalLB không dùng được**.
> Ràng buộc gốc: [`/CLAUDE.md`](../../CLAUDE.md). Manifest hiện thực: thư mục [`/deploy`](../../deploy).
> Liên quan: [`deployment-k8s.md`](deployment-k8s.md) (Ingress/NetworkPolicy/mesh),
> [`outbox-debezium.md`](../standards/outbox-debezium.md), [`resilience-flash-sale.md`](../standards/resilience-flash-sale.md).

---

## 1. Nguyên tắc chi phối

| Ràng buộc | Hệ quả lên topology |
|---|---|
| K8s thuần tự quản, **không managed cloud service** | Không dùng managed LB (AWS NLB / Vultr LB) → lớp vào bằng **ingress-nginx hostNetwork** |
| **On-prem không autoscale**, phần cứng cố định | Số node & replica **đặt cứng**, sizing cho throughput bền vững; Waiting Room hấp thụ spike |
| **Database-per-service (mức logical)** | **1 cluster Postgres dùng chung** (1 primary + 1 replica), 7 database riêng — KHÔNG mỗi service một cluster |
| Flash sale tranh chấp cao | Redis/Kafka là hot path → tách **pool data** riêng, ghim cứng, tránh nhiễu từ app |
| Stripe webhook gọi ngược | Cần node **edge** phơi public IP + TLS, đi thẳng Payment (DMZ) |

> **Vì sao không MetalLB trên VPS cloud:** cloud ảo hoá ARP — chỉ IP do provider gán cho VM
> mới resolve được, nên MetalLB L2 không "chiếm" được IP; hypervisor còn chặn ARP spoof.
> BGP cũng không peer được trong VPC chuẩn. → Trên **cả Vultr lẫn AWS EC2**, `type=LoadBalancer`
> chỉ chạy qua **managed LB của provider** (lệch ràng buộc). Giải pháp nhất quán & $0:
> **ingress-nginx chạy `hostNetwork` trên node edge**, DNS trỏ vào public IP node đó.
> (Tham chiếu: [MetalLB Cloud Compatibility](https://metallb.universe.tf/installation/clouds/).)
> MetalLB chỉ hợp khi có **bare-metal/LAN thật** (server vật lý + switch riêng).

---

## 2. Bốn node pool

Cụm chia 4 pool theo vai trò, phân tách bằng **label + taint**:

| Pool | Label node | Taint | Public IP | Vai trò |
|---|---|---|---|---|
| **master** | (mặc định) | `node-role.kubernetes.io/control-plane:NoSchedule` | không¹ | API server + etcd (control plane) |
| **data** | `workload=data` | `dedicated=data:NoSchedule` | không | Postgres, Redis, Kafka (stateful, I/O cao) |
| **app** | `workload=app` | _(không taint)_ | không | 9 service + Debezium + monitoring + ArgoCD + Operator |
| **edge** | `workload=edge` | `dedicated=edge:NoSchedule` | **có** | ingress-nginx hostNetwork (DMZ vào) |

¹ Node nói chuyện nội bộ qua **private IP**; truy cập `kubectl` qua VPN/bastion hoặc mở `6443`
trên 1 master có giới hạn IP nguồn.

**Vì sao app pool KHÔNG taint:** các thành phần hệ thống không gắn `nodeSelector` (Prometheus,
Grafana, Loki, ArgoCD, các Operator) sẽ tự rơi về node **không bị taint** = app pool. Master
bị taint control-plane, data/edge bị taint riêng → chúng tránh; nên app pool là "nơi chứa mặc
định". Cần tính dư tài nguyên app cho nhóm này.

---

## 3. Topology — Production HA (13 node)

| Pool | Số node | Spec/node (gợi ý) | Ghi chú |
|---|---|---|---|
| master | **3** | 2 vCPU / 4 GB / 40 GB | quorum etcd **số lẻ** — 2 node là SAI (mất 1 = mất quorum) |
| data | **3** | 4 vCPU / 16 GB / **100 GB SSD/NVMe** | Kafka + WAL Postgres ăn I/O → ưu tiên đĩa nhanh |
| app | **4** | 4 vCPU / 16 GB / 40 GB | ~23 pod service + monitoring + ArgoCD + operators → node thứ 4 để KHÔNG over-subscribe CPU |
| edge | **3** | 4 vCPU / 8 GB / 40 GB | terminate TLS cho cơn bão flash sale (CPU-bound) → 3 public IP, DNS round-robin |

**Tổng:** ~46 vCPU · ~148 GB RAM · 3×100 GB SSD (data) + phần còn lại.

> **Vì sao nâng từ 11 → 13 node:** hai điểm nghẽn lúc t=0 mở bán không nằm ở hot path (đã được Waiting
> Room + Redis O(1) che) mà ở **rìa**: (1) **edge** phải terminate TLS cho hàng chục nghìn kết nối nguội
> — CPU-bound → tăng 2→**3 node** và 2→**4 vCPU**, kèm CDN cho trang tĩnh + tune `worker_connections`;
> (2) **app pool** là "thùng chứa mặc định" (ôm cả monitoring/ArgoCD/operator) → 3 node bị over-subscribe
> ở mức requests, tăng lên **4 node**. Data pool vẫn **3** (Kafka RF=3 ép số 3, Postgres/Redis 1m+1r là đủ).

### 3.1 Pod nằm ở đâu (anti-affinity đã set trong manifest)

```text
master-1/2/3 : kube-apiserver, etcd, scheduler, controller-manager        (chỉ control plane)

data-1 : kafka-broker-0 | postgres-primary | redis-master  | sentinel-0
data-2 : kafka-broker-1 | postgres-replica  | redis-replica | sentinel-1
data-3 : kafka-broker-2 | sentinel-2 | minio
         → Kafka 3 broker rải đủ 3 node (anti-affinity REQUIRED);
           Postgres & Redis mỗi cái 2 bản → chiếm 2/3 node, primary≠replica;
           3 Sentinel rải 3 node để bầu master đủ quorum;
           MinIO (object storage) rơi vào data-3 đang dư (không hot-path, I/O nhẹ hơn Kafka WAL).

app-1/2/3/4 : apigateway×3, auth×2, catalog×2, inventory×3, order×3,
              payment×2, ticket×2, notification×2, waitingroom×2, debezium-connect×2
              (~23 pod, anti-affinity MỀM trải đều trên 4 node)
            + Prometheus, Grafana, Loki, ArgoCD, CloudNativePG/Strimzi/Redis operator
              (node thứ 4 để khối hệ thống này KHÔNG bóp CPU của service)

edge-1/2/3 : ingress-nginx (hostNetwork :80/:443)
             DNS: api.<domain> + webhook.<domain>  →  round-robin 3 public IP
```

### 3.2 Vì sao đúng **3** node data
- **Kafka** cấu hình `replication.factor=3, min.insync.replicas=2` (bền cho luồng tiền/vé)
  → cần **3 broker trên 3 host khác nhau** (anti-affinity *required*). Đây là yếu tố ép số 3.
- Postgres (2) và Redis (2) chỉ chiếm 2/3 node → data-3 hơi dư; **MinIO** (object storage 1 instance)
  đặt vào đây cho đỡ phí. Có thể nâng Postgres/Redis lên 3 bản nếu muốn đọc nhiều hơn, nhưng 1m+1r là đủ (xem §5).

---

## 4. Lập lịch — cơ chế ghim workload

Ghim "cố định mỗi node một thứ" thực hiện bằng **nodeSelector + taint/toleration + podAntiAffinity**,
KHÔNG ghim tay từng pod. Toàn bộ đã nhúng sẵn trong manifest `deploy/`.

### 4.1 Gắn nhãn & taint node (chạy 1 lần)
```bash
# Data (Postgres/Redis/Kafka) — taint để app không tràn vào
kubectl label node <data-1> <data-2> <data-3> workload=data
kubectl taint node <data-1> <data-2> <data-3> dedicated=data:NoSchedule

# App (9 service + Debezium + monitoring + argocd) — 4 node
kubectl label node <app-1> <app-2> <app-3> <app-4> workload=app

# Edge (ingress, public IP) — 3 node
kubectl label node <edge-1> <edge-2> <edge-3> workload=edge
kubectl taint node <edge-1> <edge-2> <edge-3> dedicated=edge:NoSchedule
```

### 4.2 Workload đọc nhãn/taint ở đâu

| Workload | Cơ chế trong manifest |
|---|---|
| App (chart `service`) | `nodeSelector: workload=app` + `podAntiAffinity` mềm theo `app=<name>` → [`deploy/charts/service`](../../deploy/charts/service) |
| Postgres (CNPG) | `spec.affinity`: `nodeSelector workload=data` + toleration `dedicated=data` + `podAntiAffinityType: required`, `topologyKey hostname` → [`deploy/infra/postgres/cluster.yaml`](../../deploy/infra/postgres/cluster.yaml) |
| Redis (OT Operator) | `spec.nodeSelector workload=data` + `tolerations dedicated=data` (Replication + Sentinel) → [`deploy/infra/redis/redis.yaml`](../../deploy/infra/redis/redis.yaml) |
| Kafka (Strimzi) | `spec.template.pod`: `nodeAffinity workload=data` + `tolerations` + `podAntiAffinity hostname` → [`deploy/infra/kafka/kafka.yaml`](../../deploy/infra/kafka/kafka.yaml) |
| Ingress (edge) | `nodeSelector workload=edge` + `tolerations dedicated=edge` + `hostNetwork=true` (DaemonSet → 1 controller/node edge) → [`deploy/edge/ingress-nginx.values.yaml`](../../deploy/edge/ingress-nginx.values.yaml), cài bằng Helm lúc bootstrap (NGOÀI ArgoCD) |
| Kafka Connect / Debezium | Strimzi `KafkaConnect`: `template.pod.nodeSelector workload=app` (chạy pool **app**, KHÔNG data) → [`deploy/infra/kafka/connect.yaml`](../../deploy/infra/kafka/connect.yaml) + connector trong [`connectors/`](../../deploy/infra/kafka/connectors) |
| MinIO (object storage) | `nodeSelector workload=data` + toleration `dedicated=data` → [`deploy/infra/minio/minio.yaml`](../../deploy/infra/minio/minio.yaml) |

> CloudNativePG & Redis Operator tự đặt anti-affinity cho primary/replica; ta chỉ cần đẩy chúng
> vào pool data. Với Kafka phải khai báo anti-affinity *required* thủ công để mỗi broker 1 node.

---

## 5. Sizing & vì sao đủ tải

### 5.1 Postgres — 1 cluster, 1 primary + 1 replica
- **Database-per-service ở mức logical**: 7 database (`auth`, `catalog`, … `notification`) trong
  cùng cluster, chung role `app`, tạo bằng `postInitSQL`. Service không đụng DB của nhau.
- **Replica** để HA (failover tự động) + offload đọc (`ticketing-db-ro`). Ghi vẫn dồn primary.
- `wal_level=logical` cho Debezium CDC (outbox).
- Nâng `instances` khi cần nhiều standby hơn; nâng tài nguyên trước khi nghĩ tới sharding.

### 5.2 Redis — 1 master + 1 replica + 3 Sentinel
- Hot path đều **O(1)**: `DECRBY` (counter GA), `SET NX`+TTL (seat hold), sorted set (waiting room),
  rate limit. Một master thừa sức vài chục–trăm nghìn op/s.
- **Replica KHÔNG chia tải ghi** — chỉ master nhận write; replica để đọc/failover. Thêm replica
  là tăng **HA**, không tăng throughput ghi.
- **Waiting Room đã throttle admission** theo nhịp hạ nguồn chịu được → tải đập vào Redis đã bị
  chặn trước → 1 master là đủ.
- Khi nào cần hơn: master saturate CPU → chuyển **Redis Cluster (nhiều master shard)**, lúc đó
  key `DECRBY`/`SETNX` cần tính hash-tag. **Chưa cần** ở quy mô này.
- Sentinel cần **3** (số lẻ) để đạt quorum bầu master; Sentinel rất nhẹ.

### 5.3 Kafka — 3 broker (KRaft)
- `replication.factor=3, min.insync.replicas=2` → chịu mất 1 broker mà không mất dữ liệu/đình trệ.
- KRaft (không ZooKeeper) → broker kiêm controller, gọn cho on-prem.

### 5.4 App — replica cố định
apigateway 3 · auth 2 · catalog 2 · inventory **3** · order **3** · payment 2 · ticket 2 ·
notification 2 · waitingroom 2 (inventory/order/apigateway cao hơn vì nằm trên hot path mua vé).
Đặt `resources.requests/limits` sát sizing (không autoscale).

### 5.5 Postgres connection pool — ngân sách phải đóng
**Bẫy của "1 cluster Postgres dùng chung":** tổng connection mọi service pool phải ≤ `max_connections`
của primary, nếu không flash sale sẽ gặp `FATAL: sorry, too many clients already`. Mặc định nguy hiểm:
**16 pod có DB × Hikari mặc định 10 = 160 > 100** (mặc định CloudNativePG). → chốt cứng **cả hai đầu**:

| Đầu | Cấu hình | Ở đâu |
|---|---|---|
| Postgres | `max_connections=200`, `superuser_reserved_connections=10` | [`deploy/infra/postgres/cluster.yaml`](../../deploy/infra/postgres/cluster.yaml) |
| App | Hikari `maximum-pool-size` + `minimum-idle` (đặt **bằng nhau** = pre-warm), `connection-timeout=3000` | [`deploy/apps/<svc>.values.yaml`](../../deploy/apps) |

**Ngân sách (pool × replica):**

| Service | Pool | Replica | Tổng | Vai trò |
|---|---|---|---|---|
| order | 10 | 3 | 30 | hot path (saga + outbox) |
| inventory | 10 | 3 | 30 | hot path (ghi SOLD) |
| payment | 8 | 2 | 16 | gọi Stripe, ghi payment |
| catalog | 8 | 2 | 16 | đọc nhiều (cache mạnh) |
| auth | 8 | 2 | 16 | login/đăng ký |
| ticket | 8 | 2 | 16 | sinh vé |
| notification | 5 | 2 | 10 | consumer async |
| **Tổng** | | | **134** | chừa ~66 cho replication/Debezium/admin/exporter |

- `minimum-idle = maximum-pool-size` → pool **mở sẵn lúc khởi động**, KHÔNG phình giữa spike (tránh
  cơn bão mở connection đúng lúc t=0).
- `connection-timeout=3000` (3s) → pod đói connection **lỗi nhanh** thay vì treo thread 30s rồi sập dây chuyền.
- Primary 4 vCPU chỉ chạy song song hiệu quả ~8–16 query; 134 connection phần lớn *idle* (~10 MB/connection
  ≈ 1.3 GB, vừa với node 16 GB). Đọc nặng (catalog) có thể trỏ `ticketing-db-ro` để nhẹ primary.
- **Khi nào cần hơn:** replica/service tăng vượt ngân sách → đặt **PgBouncer (CNPG `Pooler`, transaction mode)**
  trước primary, gom hàng trăm "connection" phía app về vài chục backend connection. Chưa cần ở quy mô này.
- apigateway & waitingroom **không có DB** → không tính vào ngân sách (apigateway tuyệt đối không JDBC).

### 5.6 Object storage (MinIO) & Debezium Connect
- **MinIO** thay managed S3 (on-prem không có): 1 instance + 1 PVC trên pool data, bucket `event-images`
  (public — origin cho CDN) + `ticket-qr`. KHÔNG hot-path lúc flash sale → 1 instance đủ; cần HA thì
  chuyển **MinIO distributed (≥4 drive)** hoặc **MinIO Operator (Tenant)**.
- **Kafka Connect (Debezium)** chạy trên pool **app** (2 worker, khớp `debezium-connect×2` §3.1), KHÔNG
  trên data — nó stateless, chỉ giữ offset trong Kafka. Mỗi service producer = 1 `KafkaConnector` CRD
  (auth/order/payment), `tasks.max=1`; scale bằng tăng worker, task tự rebalance.
- **Ngân sách slot Postgres:** mỗi connector giữ 1 replication slot + 1 walsender trên **cùng instance**
  Postgres dùng chung → `max_replication_slots`/`max_wal_senders` (đặt **10/10** ở `cluster.yaml`) phải
  ≥ số connector. 3 connector hiện tại → còn dư; >10 producer thì nâng. (Đây là ngân sách theo *instance*
  vì 1 cluster dùng chung, không phải cluster-per-service.)

---

## 6. Mạng

```text
            Internet (Stripe, người dùng)
                  │  HTTPS (TLS bắt buộc)
        ┌─────────┴───────────────────┐
        │  DNS round-robin            │   api.<domain> , webhook.<domain>
        ▼            ▼                ▼
   edge-1 (IP)  edge-2 (IP)     edge-3 (IP)     ← chỉ edge phơi :80/:443 (3 IP)
        │  ingress-nginx hostNetwork
        │   ├─ /            → Service apigateway:8080   (verify JWT, rate limit)
        │   └─ /webhooks/.. → Service payment:8086      (DMZ, verify chữ ký Stripe, KHÔNG JWT)
        ▼
   ───────────── mạng PRIVATE (VPC) — toàn bộ node-to-node ─────────────
   app pool  ←→  data pool   (K8s DNS nội bộ: ticketing-db-rw, redis-sentinel, ticketing-kafka-bootstrap)
```

- **Bật VPC/private network** giữa các instance; set `--node-ip` = **private IP** khi join cluster
  → Kafka replication, Postgres streaming, Redis traffic đi private (nhanh, không tốn bandwidth public).
- **Chỉ edge** có public IP và mở 80/443. Data/app/master không phơi ra ngoài.
- **TLS** ở Ingress (Stripe chỉ gửi tới HTTPS) — dùng cert-manager + Let's Encrypt hoặc cert tự quản.
- **CDN ảnh** (dịch vụ ngoài, vd Cloudflare): proxy host `img.<domain>` và cache tại POP — cluster chỉ
  phục vụ cache-miss. **Origin trong cluster** = Ingress [`deploy/ingress/images-ingress.yaml`](../../deploy/ingress/images-ingress.yaml)
  phơi **chỉ** bucket public MinIO `event-images` (path `/event-images`, Cache-Control immutable),
  KHÔNG lộ `ticket-qr`/S3 API. Seat map + trang waiting-room tĩnh cũng nên đẩy sau CDN tương tự.
  → cơn bão tải ảnh lúc t=0 KHÔNG đập vào edge/MinIO. Dev thay bằng `cloudflared` tunnel.
- **Edge HA**: 3 public IP + **DNS round-robin** (VPS cloud không có floating IP L2 thật) — mất 1 edge
  chỉ ảnh hưởng ~1/3 thay vì 1/2. Nếu provider có **Reserved IP**, gắn vào edge và chuyển bằng script khi node chết.
- Chi tiết Ingress/NetworkPolicy/mesh: xem [`deployment-k8s.md`](deployment-k8s.md).

---

## 7. Chịu lỗi (mất 1 node)

| Mất node | Hậu quả | Tự hồi phục? |
|---|---|---|
| 1 master (còn 3) | control plane vẫn quorum (2/3) | ✅ |
| 1 data | Kafka còn 2 broker (min ISR 2 vẫn ghi được); Postgres failover sang replica; Redis Sentinel bầu master mới | ✅ (vài giây–chục giây) |
| 1 app (còn 3) | pod dời sang app node còn lại (anti-affinity mềm, có chỗ) | ✅ |
| 1 edge (còn 2) | DNS round-robin còn 2 IP; ~1/3 request lỗi tới khi client/DNS loại IP chết | ⚠️ một phần |

> Điểm yếu còn lại là **edge** (giới hạn của VPS cloud không có LB/VIP thật). Chấp nhận DNS RR,
> hoặc đặt managed LB của provider trước 3 edge **nếu** chịu lệch ràng buộc "no managed cloud".

---

## 8. Thứ tự bootstrap (khớp `deploy/`)

1. Dựng cụm K8s (3 master + 4 app + 3 data + 3 edge), gắn **nhãn/taint** (§4.1), bật VPC private.
2. Cài bằng **Helm (NGOÀI ArgoCD)**: **ArgoCD**; **ingress-nginx hostNetwork** pool edge
   ([`deploy/edge/ingress-nginx.values.yaml`](../../deploy/edge/ingress-nginx.values.yaml)); **cert-manager**
   + apply [`deploy/edge/cluster-issuer.yaml`](../../deploy/edge/cluster-issuer.yaml) (sửa `email` trước).
3. `kubectl apply` các App ArgoCD: `infra-operators` (wave −2) → `infra-resources` (wave −1) →
   `applicationset` (app, wave 0) → `extras-app` (Ingress). Xem [`deploy/README.md`](../../deploy/README.md).
4. Tạo tay 2 secret: `payment-stripe`, `auth-jwt` (DB do CloudNativePG tự sinh `ticketing-db-app`).
5. Trỏ DNS `api.<domain>` + `webhook.<domain>` → **3 bản ghi A round-robin** vào public IP node edge
   (xem [`deploy/README.md`](../../deploy/README.md) §DNS); đăng ký webhook ở Stripe.

CI/CD sau đó: GitHub Actions build+push image (Docker Hub, tạm) → bump tag → ArgoCD sync.
