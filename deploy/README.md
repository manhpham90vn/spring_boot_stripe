# Deploy lên Kubernetes (GitHub Actions → Docker Hub → ArgoCD)

Luồng GitOps:

```text
git push (main)
  └─> GitHub Actions (.github/workflows/ci.yml)
        ├─ test:        ./mvnw verify (reactor: libs trước, rồi 9 service)
        ├─ build-push:  buildx build + push docker.io/<user>/<svc>:git-<sha7>
        └─ bump-tags:   yq sửa image.tag trong deploy/apps/*.values.yaml + commit [skip ci]
                              │
                       ArgoCD (in-cluster) phát hiện diff
                              └─> sync vào namespace "ticketing"
```

Jenkins/Kaniko/Harbor đã bỏ. Image lưu **tạm** trên Docker Hub (lệch ràng buộc on-prem
trong CLAUDE.md — khi lên thật chỉ cần đổi `image.repo` sang registry on-prem).

## Cấu trúc

```text
deploy/
  charts/service/        # 1 Helm chart generic dùng cho cả 9 service app
  apps/<svc>.values.yaml # values riêng từng service (CI bump image.tag ở đây)
  infra/                 # HẠ TẦNG quản qua GitOps (CR cho Operator)
    postgres/            #   1 Cluster CloudNativePG dùng chung (7 database riêng)
    redis/               #   Redis 1 master + 1 replica + Sentinel (OT Operator)
    kafka/               #   Kafka KRaft + NodePool (Strimzi)
      connect.yaml       #     KafkaConnect (Debezium plugin) — đăng ký connector qua CRD
      connectors/        #     KafkaConnector outbox cho auth/order/payment (prod thay connect-init)
    minio/               #   Object storage tự host (event-images + ticket-qr) — thay S3 managed
    monitoring/          #   ServiceMonitor cho Prometheus
  argocd/
    infra-operators.yaml #   4 App: CloudNativePG, Strimzi, Redis Operator, kube-prometheus-stack (wave -2)
    infra-resources.yaml #   App sync deploy/infra (wave -1)
    applicationset.yaml  #   sinh 9 App từ list, multi-source ($values) (wave 0)
    extras-app.yaml      #   App đồng bộ deploy/ingress
  ingress/               # Ingress apigateway + Ingress webhook Stripe (DMZ), có TLS cert-manager
  edge/                  # Lớp VÀO (bootstrap, NGOÀI ArgoCD): ingress-nginx hostNetwork + ClusterIssuer
  secrets/secrets.example.yaml  # MẪU — tạo secret thật bằng kubectl, KHÔNG commit
```

Sync-wave đảm bảo thứ tự: Operator (tạo CRD) → CR hạ tầng → app.

## Hạ tầng được cài luôn trong repo (GitOps)

| Thành phần | Operator/Chart | CR trong repo | DNS app dùng |
|---|---|---|---|
| PostgreSQL | CloudNativePG | `infra/postgres/cluster.yaml` (1 cluster, 7 database) | `ticketing-db-rw:5432/<db>` |
| Redis (HA) | OT Redis Operator | `infra/redis/redis.yaml` | Sentinel `redis-sentinel:26379`, master `myMaster` |
| Kafka | Strimzi (KRaft) | `infra/kafka/kafka.yaml` | `ticketing-kafka-bootstrap:9092` |
| Kafka Connect + Debezium | Strimzi | `infra/kafka/connect.yaml` + `infra/kafka/connectors/*` | outbox → topic `<svc>.events` |
| Object storage | MinIO (self-host) | `infra/minio/minio.yaml` | `http://minio:9000` (S3 API) |
| Prometheus + Grafana | kube-prometheus-stack | `infra/monitoring/servicemonitor.yaml` | scrape `/actuator/prometheus` |

- **Database-per-service (mức logical)**: 1 cluster Postgres dùng chung (1 primary + 1 replica),
  bên trong tạo 7 database riêng (`postInitSQL`), chung role `app`. CloudNativePG tự sinh secret
  `ticketing-db-app` → app đọc user/pass qua `valueFrom` (không tạo tay). Tăng `instances` khi cần HA hơn.
- **Redis**: 1 master + 1 replica + Sentinel. Chỉ master nhận write; op hot path O(1) nên đủ tải
  (Waiting Room đã throttle spike). App dùng `SPRING_DATA_REDIS_SENTINEL_*` (set ở `charts/service/values.yaml`).
- **Grafana** đi kèm kube-prometheus-stack (đăng nhập lấy mật khẩu từ secret `kube-prom-grafana`).
- **Debezium (outbox CDC)**: prod KHÔNG dùng `connect-init` POST REST như dev — Strimzi `KafkaConnect`
  (`infra/kafka/connect.yaml`) tự build image kèm plugin Debezium Postgres, rồi reconcile các
  `KafkaConnector` trong `infra/kafka/connectors/` (auth/order/payment). Mỗi connector trỏ
  `ticketing-db-rw/<db>`, đọc user/pass DB từ secret `ticketing-db-app` (DirectoryConfigProvider),
  slot/publication duy nhất. Thêm producer mới = thêm 1 file connector (xem `outbox-debezium.md §7.3`).
- **MinIO (object storage)**: tự host vì on-prem không có managed S3. 1 instance + 1 PVC,
  bucket `event-images` (public, làm origin cho CDN) + `ticket-qr`. Root cred từ secret `minio-creds`.
  Cần HA → chuyển MinIO distributed/Operator.
- **CDN**: KHÔNG phải manifest trong cluster — là dịch vụ ngoài (Cloudflare/provider CDN) đứng trước
  node edge, cache tài sản tĩnh (seat map, ảnh sự kiện từ `event-images`, trang waiting-room) để
  spike tải trang KHÔNG đập vào edge. Dev dùng `cloudflared` tunnel thay thế (xem docker-compose).

## Phải sửa trước khi dùng

1. GitHub repo Settings:
   - Variables: `DOCKERHUB_USER`
   - Secrets: `DOCKERHUB_TOKEN` (Docker Hub Access Token)
2. Thay `REPLACE_ME`:
   - `deploy/apps/*.values.yaml` → `docker.io/<DOCKERHUB_USER>/<svc>` (khớp user ở CI)
   - `deploy/argocd/*.yaml` (`applicationset`, `extras-app`, `infra-resources`) → `repoURL` repo GitHub
   - `deploy/infra/kafka/connect.yaml` → `image: docker.io/<DOCKERHUB_USER>/ticketing-connect` (nơi
     Strimzi push image Connect đã build). Cần secret `dockerhub` (push + pull) — xem bootstrap.
3. Đổi `host:` trong `deploy/ingress/*.yaml` sang domain thật (khớp cả `tls.hosts`), trỏ DNS
   `api.<domain>` + `webhook.<domain>` round-robin vào public IP các node edge.
4. Sửa `email` trong `deploy/edge/cluster-issuer.yaml` (ACME Let's Encrypt).
5. Pin lại version chart trong `argocd/infra-operators.yaml` cho khớp cluster.
6. **App cần dependency `micrometer-registry-prometheus`** để có endpoint `/actuator/prometheus`
   (thêm vào pom mỗi service nếu chưa có) — nếu không ServiceMonitor scrape sẽ 404.

## Bootstrap cluster (một lần)

```bash
# 1. Gắn nhãn + taint node theo 4 pool (cluster-topology.md §4.1) — chạy 1 lần
kubectl label node data-1 data-2 data-3 workload=data
kubectl taint node data-1 data-2 data-3 dedicated=data:NoSchedule
kubectl label node app-1 app-2 app-3 app-4 workload=app
kubectl label node edge-1 edge-2 edge-3 workload=edge
kubectl taint node edge-1 edge-2 edge-3 dedicated=edge:NoSchedule

# 2. Lớp VÀO, KHÔNG do ArgoCD quản (cluster-topology.md §1/§6/§8):
#    ingress-nginx hostNetwork trên pool edge — KHÔNG MetalLB (VPS cloud ARP ảo hoá)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace -f deploy/edge/ingress-nginx.values.yaml
#    cert-manager (TLS cho Ingress — Stripe webhook bắt buộc HTTPS)
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace --set crds.enabled=true
kubectl apply -f deploy/edge/cluster-issuer.yaml   # sửa email trước khi apply

# 3. ArgoCD
helm repo add argo https://argoproj.github.io/argo-helm
helm install argocd argo/argo-cd -n argocd --create-namespace

# 4. Namespace + secret tạo tay (DB do CloudNativePG tự sinh — KHÔNG cần tạo)
kubectl create namespace ticketing
kubectl -n ticketing create secret generic payment-stripe \
  --from-literal=STRIPE_API_KEY='sk_live_xxx' \
  --from-literal=STRIPE_WEBHOOK_SECRET='whsec_xxx'
kubectl -n ticketing create secret generic auth-jwt \
  --from-file=jwt-private.pem=infra/keys/jwt-private.pem \
  --from-file=jwt-public.pem=infra/keys/jwt-public.pem
kubectl -n ticketing create secret generic minio-creds \
  --from-literal=root-user='minioadmin' --from-literal=root-password='<mật-khẩu-mạnh>'
# Docker Hub: pushSecret cho Kafka Connect build image + pull (bắt buộc, kể cả repo public)
kubectl -n ticketing create secret docker-registry dockerhub \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username='<user>' --docker-password='<token>'

# 5. Đăng ký toàn bộ App cho ArgoCD (operator → CR hạ tầng → app, theo sync-wave)
kubectl apply -f deploy/argocd/infra-operators.yaml
kubectl apply -f deploy/argocd/infra-resources.yaml
kubectl apply -f deploy/argocd/applicationset.yaml
kubectl apply -f deploy/argocd/extras-app.yaml

# 6. Trỏ DNS (xem mục "DNS" bên dưới) rồi đăng ký webhook ở Stripe Dashboard:
#    https://webhook.<domain>/webhooks/stripe
```

Từ đây mỗi lần merge vào `main`, CI build/push ảnh + bump tag, ArgoCD tự rollout.

## DNS (trỏ domain vào pool edge)

VPS cloud KHÔNG có LB/VIP L2 thật → **HA lớp vào bằng DNS round-robin**: tạo **3 bản ghi A
trùng tên** cho mỗi host, trỏ vào public IP của **cả 3 node edge** (`edge-1/2/3`).
Resolver trả luân phiên các IP; client tự thử IP còn lại nếu một IP chết (xem topology §6/§7).

```dns
; thay <edge-N-ip> bằng public IP node edge, <domain> bằng domain thật
api.<domain>.        300  IN  A  <edge-1-ip>   ; traffic nghiệp vụ → apigateway
api.<domain>.        300  IN  A  <edge-2-ip>
api.<domain>.        300  IN  A  <edge-3-ip>
webhook.<domain>.    300  IN  A  <edge-1-ip>   ; webhook Stripe (DMZ) → payment
webhook.<domain>.    300  IN  A  <edge-2-ip>
webhook.<domain>.    300  IN  A  <edge-3-ip>
```

- **TTL ngắn (300s)** để rút IP node chết khỏi vòng quay nhanh khi cần.
- 2 host (`api`, `webhook`) **cùng** trỏ vào edge — ingress-nginx tách route theo host:
  `api.<domain>` → `apigateway:8080`, `webhook.<domain>` → `payment:8086` (DMZ, bỏ qua gateway).
- Domain phải **khớp** `host:` + `tls.hosts` trong `deploy/ingress/*.yaml` (cert-manager xin cert
  Let's Encrypt theo đúng tên này).
- Nếu provider có **Reserved/Floating IP**: gắn vào 1 edge, dùng A record đơn + script chuyển IP
  khi node chết (failover nhanh hơn DNS round-robin) — xem topology §6.
- Kiểm tra: `dig +short api.<domain>` phải trả về **cả 2** IP edge.

## Kiểm tra chart local

```bash
helm template t deploy/charts/service -f deploy/apps/auth.values.yaml
```
