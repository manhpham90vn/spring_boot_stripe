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

## 3. Topology — Production HA (11 node)

| Pool | Số node | Spec/node (gợi ý) | Ghi chú |
|---|---|---|---|
| master | **3** | 2 vCPU / 4 GB / 40 GB | quorum etcd **số lẻ** — 2 node là SAI (mất 1 = mất quorum) |
| data | **3** | 4 vCPU / 16 GB / **100 GB SSD/NVMe** | Kafka + WAL Postgres ăn I/O → ưu tiên đĩa nhanh |
| app | **3** | 4 vCPU / 16 GB / 40 GB | gánh cả monitoring + ArgoCD + operators |
| edge | **2** | 2 vCPU / 4 GB / 40 GB | 2 public IP → DNS round-robin |

**Tổng:** ~34 vCPU · ~116 GB RAM · 3×100 GB SSD (data) + phần còn lại.

### 3.1 Pod nằm ở đâu (anti-affinity đã set trong manifest)

```text
master-1/2/3 : kube-apiserver, etcd, scheduler, controller-manager        (chỉ control plane)

data-1 : kafka-broker-0 | postgres-primary | redis-master  | sentinel-0
data-2 : kafka-broker-1 | postgres-replica  | redis-replica | sentinel-1
data-3 : kafka-broker-2 | sentinel-2
         → Kafka 3 broker rải đủ 3 node (anti-affinity REQUIRED);
           Postgres & Redis mỗi cái 2 bản → chiếm 2/3 node, primary≠replica;
           3 Sentinel rải 3 node để bầu master đủ quorum.

app-1/2/3 : apigateway×3, auth×2, catalog×2, inventory×3, order×3,
            payment×2, ticket×2, notification×2, waitingroom×2, debezium-connect×2
            (~23 pod, anti-affinity MỀM trải đều)
          + Prometheus, Grafana, Loki, ArgoCD, CloudNativePG/Strimzi/Redis operator

edge-1/2 : ingress-nginx (hostNetwork :80/:443)
           DNS: api.<domain> + webhook.<domain>  →  round-robin 2 public IP
```

### 3.2 Vì sao đúng **3** node data
- **Kafka** cấu hình `replication.factor=3, min.insync.replicas=2` (bền cho luồng tiền/vé)
  → cần **3 broker trên 3 host khác nhau** (anti-affinity *required*). Đây là yếu tố ép số 3.
- Postgres (2) và Redis (2) chỉ chiếm 2/3 node → data-3 hơi dư. Có thể nâng Postgres/Redis lên
  3 bản để lấp đầy nếu muốn đọc nhiều hơn, nhưng 1m+1r là đủ (xem §5).

---

## 4. Lập lịch — cơ chế ghim workload

Ghim "cố định mỗi node một thứ" thực hiện bằng **nodeSelector + taint/toleration + podAntiAffinity**,
KHÔNG ghim tay từng pod. Toàn bộ đã nhúng sẵn trong manifest `deploy/`.

### 4.1 Gắn nhãn & taint node (chạy 1 lần)
```bash
# Data (Postgres/Redis/Kafka) — taint để app không tràn vào
kubectl label node <data-1> <data-2> <data-3> workload=data
kubectl taint node <data-1> <data-2> <data-3> dedicated=data:NoSchedule

# App (9 service + Debezium + monitoring + argocd)
kubectl label node <app-1> <app-2> <app-3> workload=app

# Edge (ingress, public IP)
kubectl label node <edge-1> <edge-2> workload=edge
kubectl taint node <edge-1> <edge-2> dedicated=edge:NoSchedule
```

### 4.2 Workload đọc nhãn/taint ở đâu

| Workload | Cơ chế trong manifest |
|---|---|
| App (chart `service`) | `nodeSelector: workload=app` + `podAntiAffinity` mềm theo `app=<name>` → [`deploy/charts/service`](../../deploy/charts/service) |
| Postgres (CNPG) | `spec.affinity`: `nodeSelector workload=data` + toleration `dedicated=data` + `podAntiAffinityType: required`, `topologyKey hostname` → [`deploy/infra/postgres/cluster.yaml`](../../deploy/infra/postgres/cluster.yaml) |
| Redis (OT Operator) | `spec.nodeSelector workload=data` + `tolerations dedicated=data` (Replication + Sentinel) → [`deploy/infra/redis/redis.yaml`](../../deploy/infra/redis/redis.yaml) |
| Kafka (Strimzi) | `spec.template.pod`: `nodeAffinity workload=data` + `tolerations` + `podAntiAffinity hostname` → [`deploy/infra/kafka/kafka.yaml`](../../deploy/infra/kafka/kafka.yaml) |
| Ingress (edge) | `nodeSelector workload=edge` + `tolerations dedicated=edge` + `hostNetwork=true` (cài qua Helm/ArgoCD) |

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

---

## 6. Mạng

```text
            Internet (Stripe, người dùng)
                  │  HTTPS (TLS bắt buộc)
        ┌─────────┴──────────┐
        │  DNS round-robin    │   api.<domain> , webhook.<domain>
        ▼                     ▼
   edge-1 (public IP)   edge-2 (public IP)      ← chỉ edge phơi :80/:443
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
- **Edge HA**: 2 public IP + **DNS round-robin** (VPS cloud không có floating IP L2 thật). Nếu
  provider có **Reserved IP**, gắn vào 1 edge và chuyển bằng script khi node chết.
- Chi tiết Ingress/NetworkPolicy/mesh: xem [`deployment-k8s.md`](deployment-k8s.md).

---

## 7. Chịu lỗi (mất 1 node)

| Mất node | Hậu quả | Tự hồi phục? |
|---|---|---|
| 1 master (còn 3) | control plane vẫn quorum (2/3) | ✅ |
| 1 data | Kafka còn 2 broker (min ISR 2 vẫn ghi được); Postgres failover sang replica; Redis Sentinel bầu master mới | ✅ (vài giây–chục giây) |
| 1 app | pod dời sang app node còn lại (anti-affinity mềm, có chỗ) | ✅ |
| 1 edge | DNS round-robin còn IP kia; ~50% request lỗi tới khi client/DNS loại IP chết | ⚠️ một phần |

> Điểm yếu còn lại là **edge** (giới hạn của VPS cloud không có LB/VIP thật). Chấp nhận DNS RR,
> hoặc đặt managed LB của provider trước 2 edge **nếu** chịu lệch ràng buộc "no managed cloud".

---

## 8. Thứ tự bootstrap (khớp `deploy/`)

1. Dựng cụm K8s (3 master + workers), gắn **nhãn/taint** (§4.1), bật VPC private.
2. Cài **ArgoCD**, **ingress-nginx hostNetwork** (pool edge), **cert-manager**.
3. `kubectl apply` các App ArgoCD: `infra-operators` (wave −2) → `infra-resources` (wave −1) →
   `applicationset` (app, wave 0) → `extras-app` (Ingress). Xem [`deploy/README.md`](../../deploy/README.md).
4. Tạo tay 2 secret: `payment-stripe`, `auth-jwt` (DB do CloudNativePG tự sinh `ticketing-db-app`).
5. Trỏ DNS `api.<domain>` + `webhook.<domain>` → public IP các node edge; đăng ký webhook ở Stripe.

CI/CD sau đó: GitHub Actions build+push image (Docker Hub, tạm) → bump tag → ArgoCD sync.
