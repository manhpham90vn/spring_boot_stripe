# Waiting Room — van chặn spike (design)

> **Trạng thái:** THIẾT KẾ định hướng, **làm CUỐI CÙNG** (theo CLAUDE.md) — chỉ dựng khi đã
> có luồng mua hoàn chỉnh để throttle. Store chính: **Redis**. Ràng buộc gốc:
> [`/CLAUDE.md`](../CLAUDE.md). Liên quan: [`resilience-flash-sale.md`](./resilience-flash-sale.md),
> [`inventory-no-oversell.md`](./inventory-no-oversell.md).

---

## 1. Vai trò
Khi mở bán, chục nghìn người ập vào cùng lúc trong khi phần cứng **cố định (không
autoscale)**. Waiting Room là **van trước spike**: giữ người trong hàng đợi và **thả vào
theo nhịp mà hạ nguồn (Inventory, Payment/Stripe) chịu được**, thay vì để dòng người đánh
sập hệ thống.

## 2. Cơ chế — Redis sorted set

```
   user xin vào ──▶ cấp số thứ tự (ticket) ──▶ ZADD queue:{eventId} score=timestamp member=userId
                                                     │
   poll vị trí  ──▶ ZRANK → "bạn đang ở vị trí N"   │
                                                     ▼
   bộ thả (admission) chạy theo NHỊP: mỗi chu kỳ lấy top-K (ZPOPMIN/ZRANGE) → cấp PASS
                                                     │
                                          PASS (token có hạn) ──▶ được phép gọi /api/order
```

- **Sorted set** giữ thứ tự FIFO theo thời điểm vào (score = timestamp) — công bằng.
- **Admission token (PASS)** có thời hạn: chỉ ai cầm PASS hợp lệ mới được vào luồng mua.
  Gateway/Order kiểm PASS trước khi cho đặt đơn.

## 3. Admission rate phải BIẾT tồn kho + ngưỡng Stripe
Đây là điểm mấu chốt: **không thả nhiều hơn số vé còn lại** và **không vượt ~100–200 req/s
của Stripe**.
- Lấy **tồn kho còn lại** từ Inventory (xem [`inventory-no-oversell.md`](./inventory-no-oversell.md))
  → nếu gần hết, giảm/ngừng thả.
- Giới hạn nhịp thả ≤ năng lực Payment/Stripe ([`payment-stripe-flow.md`](./payment-stripe-flow.md)).
- Khi hết vé: ngừng thả, báo sold-out cho phần còn lại trong hàng (đỡ tải vô ích).

## 4. Chống bot
**CAPTCHA** trước khi được xếp hàng (hoặc trước khi cấp PASS) để bot không chiếm chỗ hàng
loạt — quan trọng vì chỗ trong hàng = cơ hội mua.

## 5. Quy ước & ranh giới
- API client: `/api/waitingroom/**` (qua gateway). Vd: xin vào hàng, poll vị trí, đổi PASS.
- Store chính **Redis** (sorted set + token); **không cần PostgreSQL** riêng (trạng thái
  hàng đợi là tạm thời) — xem [`API-CONVENTIONS.md`](./API-CONVENTIONS.md) / cấu hình
  `waitingroom/application.properties`.
- Waiting Room đứng **trước** luồng mua; chỉ gác cổng, không giữ logic tồn kho/tiền.

## 6. Cạm bẫy
1. **Thả quá tay = oversell hoặc sập hạ nguồn.** Admission rate phải bám tồn kho + năng lực
   thực, điều chỉnh động.
2. **PASS phải có hạn + chống dùng lại/chia sẻ** (ký hoặc lưu Redis với TTL), nếu không
   người ta tuồn PASS cho nhau.
3. **Công bằng vs throughput**: FIFO tuyệt đối có thể phí năng lực khi người đầu hàng rời đi
   — cân nhắc bỏ qua PASS hết hạn, thả bù.
4. **Redis HA**: mất sorted set = mất hàng đợi → cần Cluster/Sentinel.
5. Bot/cào: thêm rate-limit theo IP/thiết bị + CAPTCHA.

## 7. Vì sao làm cuối cùng
Cần có **luồng mua hoàn chỉnh** (Inventory + Payment + Order) thì mới biết hạ nguồn chịu
được bao nhiêu để đặt admission rate. Dựng sớm sẽ throttle vào khoảng trống. → Hoãn tới khi
lõi đã chạy (xem thứ tự triển khai trong [`/CLAUDE.md`](../CLAUDE.md)).
