# Giải pháp cho các vấn đề trong payment_issue.md

> **Mục đích:** với MỖI vấn đề liệt kê ở [`payment_issue.md`](./payment_issue.md), nêu rõ
> **hệ thống giải quyết thế nào** và **trạng thái** (đã có trong thiết kế / cần làm khi
> code / ngoài phạm vi). Dùng làm checklist khi build `payment`/`order`/`inventory`.
>
> **Bối cảnh:** bán vé concert flash-sale, **một tài khoản Stripe**, **đa phương thức**
> (thẻ + Konbini + Furikomi), tiền **JPY**. Liên quan: [`payment-stripe-flow.md`](./payment-stripe-flow.md),
> [`saga-purchase-flow.md`](./saga-purchase-flow.md), [`inventory-no-oversell.md`](./inventory-no-oversell.md).

**Chú thích trạng thái:**
`✅` đã có trong thiết kế · `🔧` đã có hướng, cần hiện thực khi code · `⚠️` mới một phần ·
`⊘` ngoài phạm vi (CLAUDE.md / §10 payment_issue.md).

---

## 1. Tồn kho

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 1.1 | Oversell / race | Thao tác **nguyên tử Redis**: GA `DECRBY`, ghế `SET NX`+TTL — chặn oversell ngay tại điểm tranh chấp ([inventory §2](./inventory-no-oversell.md)) | ✅ |
| 1.2 | Reservation TTL | **HOLD có TTL = hạn thanh toán, theo phương thức** (thẻ phút / Konbini-Furikomi ngày) ([inventory §3.1](./inventory-no-oversell.md)) | ✅ |
| 1.3 | Nhả khi bỏ ngang | HOLD TTL tự hết hạn (Redis) + job dọn **theo hạn từng phương thức** | ✅ |
| 1.4 | Đã thu tiền nhưng hết hàng | Redis atomic chặn oversell từ bước HOLD; nếu vẫn lọt (Redis lệch) → **reconciliation** phát hiện → **auto-refund** (Konbini đi nhánh 7.3) | 🔧 |
| 1.5 | Restock khi refund/cancel | RELEASE/restock **idempotent** (đánh dấu hold đã release để chỉ +1 lần). Vé đã SOLD bị refund: **quyết định resell hay không** rồi restock tương ứng | 🔧 |

## 2. Thanh toán

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 2.1 | Trừ tiền nhiều lần | **Idempotency-Key** + **chỉ 1 PaymentIntent sống/đơn** (2.16) | 🔧 |
| 2.2 | Thiếu idempotency | **Đã làm**: Idempotency-Key (`order:{id}:attempt:1`) + UNIQUE `order_id` ở DB payment | ✅ |
| 2.3 | Webhook trễ / sai thứ tự | Xử lý theo **trạng thái CUỐI** của PI, không theo thứ tự đến; dedup (2.8) | ✅ |
| 2.4 | Tin client thay vì webhook | **Webhook = nguồn sự thật duy nhất** ([saga §2.1](./saga-purchase-flow.md)) | ✅ |
| 2.5 | Session/PI hết hạn | Xử lý `checkout.session.expired` / `payment_intent.canceled` → đóng đơn + nhả hold | 🔧 |
| 2.6 | SCA / 3DS | `requires_action` → chờ webhook, đơn ở AWAITING_PAYMENT ([payment §6](./payment-stripe-flow.md)) | ✅ |
| 2.7 | Phương thức async | **Đã làm**: saga event-driven — đơn về `AWAITING_PAYMENT`, tiếp tục khi nhận `PaymentSettled` (Payment outbox→`payment.events`→Order). Webhook async cũng phát event này | ✅ |
| 2.8 | Idempotent cả side-effect | **Đã làm**: bảng `processed_events` (dedup `event.id`) ở webhook + Ticket phát vé idempotent (`existsByOrderId`) | ✅ |
| 2.9 | Không đối chiếu amount/currency webhook | **Đã làm**: webhook so `amount`+`currency` của PI với đơn, lệch thì KHÔNG áp dụng | ✅ |
| 2.10 | Webhook song song cùng đơn | **Khóa theo đơn** (optimistic version / row-lock) khi cập nhật trạng thái | 🔧 |
| 2.11 | Capture thủ công | **Không dùng** auth-and-capture — **charge ngay** (vé bán tức thì). Tuyên bố scope | ✅(quyết định) |
| 2.12 | `payment_intent.canceled` | Handler riêng → đóng đơn + nhả hold ngay (không chờ timer) | 🔧 |
| 2.13 | Amount dưới ngưỡng / = 0 | Vé **free (¥0)** → fulfill thẳng KHÔNG gọi Stripe; validate ngưỡng JPY (~¥50) | 🔧 |
| 2.14 | Decline → retry → tiền mồ côi | Thẻ: cho retry trên **cùng PI** trong TTL; khi **bỏ cuộc phải CANCEL PI** rồi mới nhả hold ([saga §3](./saga-purchase-flow.md)) | 🔧 |
| 2.15 | Liên kết đơn ↔ PI | **Một phần**: đã ghi `order_id` vào PI `metadata`; webhook tra đơn theo metadata. Còn thiếu: lưu `paymentIntentId` vào đơn TRƯỚC khi đi (hiện lưu sau charge) | ⚠️ |
| 2.16 | Trùng session/PI một đơn | **Tối đa 1 PI sống/đơn**: reuse cái còn hạn, expire/cancel cái cũ khi đơn đổi | 🔧 |
| 2.17 | Vòng đời Idempotency-Key | **Một phần**: key ổn định `order:{id}:attempt:1`. Còn thiếu: tăng `attempt` khi đơn đổi + xử lý `idempotency_error` tường minh | ⚠️ |
| 2.18 | Gọi Stripe chiều ra thất bại | **Đã làm**: Resilience4j `@RateLimiter`+`@Retry` (chỉ 429/5xx/timeout, bọc `StripeTransientException`) + Idempotency-Key + fallback. Charge mồ côi → reconciliation (3.4, còn 🔧) | ✅ |
| 2.19 | Radar review | (tuỳ chọn, vé giá cao) chặn fulfill khi `review.opened`, mở khi `review.closed` | 🔧 |

## 3. Tính nhất quán

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 3.1 | Dual-write Stripe↔DB | **Webhook nguồn sự thật + idempotency + reconciliation**. ⚠️ Outbox CHỈ giải DB↔Kafka nội bộ, KHÔNG giải biên Stripe | 🔧 |
| 3.2 | Thiếu state machine | **Order state machine** PENDING→AWAITING_PAYMENT→PAID→COMPLETED + REJECTED/CANCELLED ([saga §3](./saga-purchase-flow.md)) | ✅ |
| 3.3 | Webhook/event mất | **Đã làm (phía saga)**: `OrderReconciliationJob` định kỳ hỏi lại Payment cho đơn kẹt `AWAITING_PAYMENT` → tự tiếp tục/bù trừ. Còn 🔧: Payment↔Stripe reconciliation (3.4) | ✅ |
| 3.4 | **Thiếu reconciliation** | **Job đối soát Stripe↔DB định kỳ** (lưới an toàn cuối) + alert khi lệch — *phải thêm, hiện chưa có* | 🔧 |
| 3.5 | Gross vs net | Đối soát kế toán/payout dùng **`balance_transaction`/net** (đã trừ phí), tách với đối soát trạng thái đơn (amount) | 🔧 |
| 3.6 | Thiếu audit & alert | **Audit log** mọi transition tiền (kèm `event.id`, from→to) + **alert** khi reconciliation lệch / refund / dispute | 🔧 |
| 3.7 | Stripe API version đổi | **Pin API version** tường minh; nâng version phải test lại toàn bộ webhook handler | 🔧 |

## 4. Bảo mật

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 4.1 | Tin giá client | Giá **tính ở server** từ Catalog (`priceMinor`+`currency`), không tin amount client | ✅ |
| 4.2 | Verify webhook + replay | **Đã làm**: `Webhook.constructEvent` (chữ ký + timestamp tolerance) + dedup `event.id` (processed_events) ở StripeWebhookService | ✅ |
| 4.3 | Quản lý secret | Secret từ **env/K8s Secret**, không commit/log; **restricted key**; quy trình rotate ([deployment §6](./deployment-k8s.md)) | ✅ |
| 4.4 | Phạm vi PCI / PAN | **Stripe.js / Payment Element** — PAN **không bao giờ chạm backend**, server chỉ thấy token/PI id | 🔧 |
| 4.5 | IDOR xem đơn người khác | Mọi truy cập đơn kiểm **`order.user_id == user`** (object-level authz), không dựa id khó đoán | 🔧 |

## 5. Hoàn tiền & khiếu nại

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 5.1 | Dispute / chargeback | Handler `charge.dispute.created` → đóng băng/đánh dấu đơn, chặn phát vé nếu chưa giao; quy trình nộp bằng chứng | 🔧 |
| 5.2 | Refund fail (kể cả muộn) | Theo dõi `charge.refund.updated`/`refund.failed`; chỉ set `refunded` khi thực sự thành công | 🔧 |
| 5.3 | Refund tay từ Dashboard | **Webhook là nguồn sự thật cho MỌI thay đổi** (kể cả thao tác tay) → đồng bộ ngược DB | ✅(hướng)/🔧 |
| 5.4 | Refund một phần | Đọc `amount_refunded` vs `amount` → `partially_refunded` vs `refunded`; nếu không hỗ trợ partial thì chặn từ đầu | 🔧 |
| 5.5 | Dòng tiền dispute | Theo dõi `funds_withdrawn`/`funds_reinstated` + **phí dispute** vào đối soát (3.5) | 🔧 |
| 5.6 | Giới hạn refund theo method | Bảng "method → khả năng/hạn refund"; Konbini cần bank info (7.3); quy trình thay thế khi ngoài hạn | 🔧 |

## 6. Tiền tệ JPY

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 6.1 | JPY zero-decimal ×100 | **Catalog đã lưu `priceMinor`(long)+`currency` ISO-4217**; thêm **hàm chuyển đổi tập trung** display↔Stripe có nhận biết zero-decimal + **test riêng JPY** | ✅(lưu trữ)/🔧(convert) |
| 6.2 | Làm tròn kiểu Nhật | Quy tắc làm tròn **tập trung, nhất quán** (hiển thị = amount Stripe = hoá đơn). Vé giá cố định ít rủi ro | ⚠️ |

## 7. Đặc thù Nhật (Konbini / Furikomi)

| # | Vấn đề | Cách giải quyết | TT |
|---|--------|-----------------|----|
| 7.1 | Konbini trả sau, pending lâu | **AWAITING_PAYMENT là bình thường**; job dọn theo **hạn từng phương thức**, không quét nhầm ([saga §2.1](./saga-purchase-flow.md), [inventory §3.1](./inventory-no-oversell.md)) | ✅ |
| 7.2 | Konbini hết hạn không trả | `async_payment_failed` → CANCELLED + nhả chỗ + restock; (tuỳ chọn) email nhắc trước hạn | 🔧 |
| 7.3 | Konbini refund không về gốc | Trạng thái **`refund_pending_customer_info`**: xin bank info của khách, theo dõi tới khi hoàn tất | 🔧 |
| 7.4 | Furikomi trả thiếu/thừa | Chính sách rõ cho thiếu/thừa/không khớp; xử lý **customer balance**; đưa "tiền lửng lơ" vào đối soát | 🔧 |
| 7.5 | Trộn sync + async | **Tham số hoá theo phương thức**: TTL giữ kho, hạn `pending`, nhắc khách — mỗi method một bộ ([inventory §3.1](./inventory-no-oversell.md), [payment §7](./payment-stripe-flow.md)) | ✅ |

---

## Ngoài phạm vi (⊘ — khớp CLAUDE.md & §10 payment_issue.md)
- **Stripe Connect / marketplace** (chia tiền nhiều seller) — KHÔNG Connect, KHÔNG Ledger.
- **Subscription / recurring** — chỉ thanh toán one-time (mua vé).
- **Multi-currency động / quy đổi FX** — mỗi loại vé một currency cố định (minor units
  + ISO-4217), KHÔNG quy đổi tỷ giá khi thanh toán.
- **Thuế & hoá đơn theo luật Nhật** (消費税 / インボイス) — tầng kế toán, ngoài kỹ thuật thanh toán.

---

## Việc PHẢI thêm trước khi go-live (tổng hợp các 🔧 trọng yếu)
1. **Reconciliation job** Stripe↔DB (3.4/3.5) — lưới an toàn cuối, hiện CHƯA có.
2. **Webhook an toàn đầy đủ**: dedup `event.id` (2.8), đối chiếu amount/currency (2.9), khóa
   theo đơn (2.10), timestamp tolerance (4.2).
3. **Vòng đời PI**: 1 PI sống/đơn (2.16), cancel khi bỏ cuộc (2.14), xử lý canceled/expired (2.5/2.12).
4. **Liên kết đơn↔PI qua metadata** (2.15) + **idempotency-key có vòng đời** (2.17).
5. **PCI**: Payment Element, PAN không chạm backend (4.4) + **IDOR** object-level (4.5).
6. **Nhánh async đầy đủ**: Konbini expiry/refund (7.2/7.3), Furikomi balance (7.4).
7. **Audit log + alert** transition tiền (3.6); **pin Stripe API version** (3.7).
