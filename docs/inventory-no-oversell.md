# Inventory — chống bán trùng dưới flash sale (design)

> **Trạng thái:** THIẾT KẾ. Bài toán đúng-sai KHÓ NHẤT của hệ thống: chục nghìn người
> tranh tồn hữu hạn cùng lúc, **tuyệt đối không bán trùng**. Ràng buộc gốc:
> [`/CLAUDE.md`](../CLAUDE.md). Liên quan: [`saga-purchase-flow.md`](./saga-purchase-flow.md),
> [`services/03-inventory.md`](./services/03-inventory.md).

---

## 1. Bài toán
- **Flash sale:** chục nghìn request đồng thời lúc mở bán.
- **Tồn hữu hạn**, không được bán quá (oversell) dù chỉ 1 vé.
- **On-prem không autoscale** → không thể "thêm máy" lúc spike; phải xử lý nhanh ở Redis,
  giảm tải Postgres.

Chìa khoá: dùng **thao tác nguyên tử của Redis** làm điểm tranh chấp (atomic, in-memory,
cực nhanh), Postgres chỉ ghi **trạng thái cuối SOLD** làm nguồn sự thật bền.

## 2. Hai loại tồn kho

### 2.1 GA (vé đứng, không đánh số ghế) — counter
- Mỗi `ticketTypeId` một key đếm: `inv:ga:{ticketTypeId}` = số còn lại.
- **Giữ chỗ = `DECRBY key n`** (nguyên tử). Kết quả:
  - ≥ 0 → giữ thành công n vé.
  - < 0 → **hết/không đủ** → `INCRBY` trả lại n, báo sold-out.
- Vì `DECRBY` nguyên tử nên hàng nghìn request song song KHÔNG thể cùng lấy quá số tồn →
  **không oversell** ngay ở tầng Redis.

### 2.2 Ghế ngồi (đánh số) — seat hold
- Mỗi ghế một khoá giữ chỗ: `SET inv:seat:{eventId}:{seatId} {orderId} NX EX {ttl}`.
  - `NX` = chỉ set nếu CHƯA tồn tại → ai chiếm trước thắng (nguyên tử).
  - `EX ttl` = chỗ tự nhả nếu saga không hoàn tất kịp.
- Trả 200 nếu giữ được hết ghế yêu cầu; nếu một ghế đã bị giữ → **nhả các ghế vừa giữ trong
  cùng request** (bù trừ cục bộ) và báo ghế-đã-có-người.

## 3. Ba thao tác (khớp với Saga)

| Bước saga | Inventory làm gì | Bền hoá |
|-----------|------------------|---------|
| **HOLD** (giữ chỗ) | GA: `DECRBY` · Ghế: `SET NX EX` → trả `holdId` | chỉ Redis (kèm TTL) |
| **COMMIT** (xác nhận SOLD sau khi tiền OK) | đánh dấu SOLD, ghi **bền** xuống Postgres | **PostgreSQL** = nguồn sự thật |
| **RELEASE** (bù trừ / hết hạn) | GA: `INCRBY` trả lại · Ghế: `DEL` khoá ghế | Redis |

- **HOLD có TTL** → nếu saga chết, chỗ tự nhả, không khoá tồn vĩnh viễn (xem Saga §7).
- **COMMIT** mới ghi Postgres — giảm ghi DB lúc cao điểm (đa số request dừng ở HOLD/RELEASE).

### 3.1 TTL hold THEO PHƯƠNG THỨC thanh toán (card vs async)

Hệ thống hỗ trợ **đa phương thức**: thẻ (đồng bộ, tiền về gần như ngay) và **Konbini /
Furikomi (bất đồng bộ, tiền về sau VÀI GIỜ → VÀI NGÀY)**. Một TTL chung sẽ sai ở một phía,
nên **TTL = hạn thanh toán, tham số hoá theo phương thức** (khớp payment_issue.md 2.7, 7.5):

| Phương thức | Tính chất | TTL hold = hạn thanh toán |
|-------------|-----------|---------------------------|
| Thẻ (card) | đồng bộ, có thể qua 3DS | ngắn — vài phút (vd 15') |
| Konbini | trả tiền mặt tại cửa hàng sau | dài — vài giờ→ngày (vd 24–72h) |
| Furikomi | chuyển khoản | dài — theo hạn cấu hình |

**Hệ quả quan trọng cho flash-sale:** hold của đơn async **vẫn trừ tồn ngay** (DECRBY/SET NX)
và **giữ suốt thời gian chờ trả** → số "còn lại" hiển thị cho người khác đã loại các hold này
ra (KHÔNG oversell), nhưng **tồn hữu hiệu giảm** trong lúc chờ ("kho ảo" tạm thời). Đây là
**đánh đổi chấp nhận có chủ đích**. Để kiểm soát, có thể:
- Đặt **quota riêng cho phương thức async** (vd tối đa N% tồn được giữ bởi Konbini/Furikomi
  cùng lúc) → phần còn lại luôn dành cho thẻ "mua là có ngay".
- Hạn Konbini/Furikomi **ngắn hợp lý** + email nhắc trước hạn (giảm tỉ lệ giữ chỗ rồi bỏ).

> ⚠️ **Đơn async để `pending` lâu là BÌNH THƯỜNG** — job dọn đơn `pending`/nhả hold phải
> dựa **đúng hạn theo phương thức**, KHÔNG quét nhầm đơn Konbini đang chờ (payment_issue.md 7.1).

## 4. API nội bộ (chỉ Order gọi)
Theo [`API-CONVENTIONS.md`](./API-CONVENTIONS.md), đặt dưới `/internal/**`:
```
POST   /internal/holds            { ticketTypeId|seatIds, qty, orderId }  → { holdId }
POST   /internal/holds/{id}/commit                                        → SOLD (ghi Postgres)
DELETE /internal/holds/{id}                                               → nhả chỗ (bù trừ)
```
Không expose ra `/api`; rào bằng NetworkPolicy ([`deployment-k8s.md`](./deployment-k8s.md)).

## 5. Idempotency (bắt buộc)
- HOLD theo `orderId`: gọi lại trả **cùng holdId**, không trừ tồn lần hai.
- COMMIT/RELEASE: lặp lại không đổi kết quả (kiểm trạng thái hold trước khi tác động).
- Chống "INCRBY trả lại hai lần" khi RELEASE bị gọi trùng → đánh dấu hold đã release.

## 6. Nguồn sự thật & reconciliation
- **Redis = trạng thái nóng** (counter/hold) có thể mất nếu Redis sự cố → cần **HA**
  (Cluster/Sentinel) và khôi phục.
- **PostgreSQL = nguồn sự thật bền** cho SOLD. Khi khởi động lại / nghi lệch, **dựng lại
  counter Redis** từ: `tổng tồn ban đầu − SOLD(Postgres) − hold đang còn hạn`.
- Job reconciliation định kỳ đối chiếu Redis vs Postgres, sửa lệch (vd hold quá hạn còn sót).

## 7. Vì sao KHÔNG khoá ở Postgres (SELECT ... FOR UPDATE)
- Khoá hàng/`SELECT FOR UPDATE` trên một dòng tồn kho dưới flash sale → **nghẽn cổ chai**:
  mọi request xếp hàng chờ khoá, kết nối DB cạn, đổ sập. On-prem không autoscale càng nguy.
- Redis atomic counter chịu được hàng chục nghìn op/s, không khoá hàng → đúng cho điểm tranh chấp.
- Postgres chỉ nhận ghi SOLD (đã thắng tranh chấp) → tải thấp, ổn định.

## 8. Cạm bẫy
1. **TTL hold phải khớp PHƯƠNG THỨC** (xem §3.1): thẻ vài phút; Konbini/Furikomi vài
   giờ→ngày. Dài hơn thời gian chờ trả của phương thức đó, nhưng đủ ngắn để nhả chỗ kẹt.
2. **Race khi COMMIT vs TTL hết hạn**: hold vừa hết TTL (Redis nhả) đúng lúc COMMIT → phải
   xử lý: COMMIT kiểm hold còn hợp lệ; nếu đã nhả thì saga thất bại có kiểm soát (không SOLD).
3. **Redis mất dữ liệu** = mất trạng thái hold → phải HA + reconciliation từ Postgres.
4. **Đừng tin client gửi số tồn**; mọi quyết định tồn kho ở server (Redis), giá/loại vé ở
   Catalog (xem [`architecture.md`](./architecture.md)).
5. Waiting Room ([`waiting-room.md`](./waiting-room.md)) **admission rate phải biết tồn còn
   lại** để không thả người vào nhiều hơn vé.

## 9. Phạm vi triển khai
**Cả hai loại tồn trong phạm vi:** GA (counter Redis `DECRBY`) và **ghế ngồi** (seat hold
`SET NX` + TTL). Lát cắt thẻ + GA chạy trước cho tiền end-to-end; ghế ngồi dùng chung
khung HOLD/COMMIT/RELEASE (xem [`services/03-inventory.md`](./services/03-inventory.md)).
