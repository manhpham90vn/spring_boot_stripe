# Tài liệu — Nền tảng bán vé sự kiện

Chỉ mục toàn bộ doc. Ràng buộc gốc (bất biến) ở [`/CLAUDE.md`](../CLAUDE.md).

## Bắt đầu
- [**dev-runbook.md**](./dev-runbook.md) — dựng stack local, cổng/công cụ, smoke-test, debug, seed admin.
- [**architecture.md**](./architecture.md) — tổng quan service, ràng buộc, khuôn chuẩn (đọc trước).

## Thiết kế chi tiết theo service (căn cứ triển khai)
- [**services/**](./services/README.md) — mỗi service một file: DB (DDL), Redis, API, event, invariant.
- [**impl/01-payment-real-stripe.md**](./impl/01-payment-real-stripe.md) — spec triển khai TỪNG FILE cho thanh toán Stripe thật (card + async Konbini/Furikomi).

## Đã hiện thực (bám sát code đang chạy)
- [**jwt-authentication.md**](./jwt-authentication.md) — xác thực JWT (RS256, JWKS, verify cục bộ).
- [**SECURITY-ACCESS-CONTROL.md**](./SECURITY-ACCESS-CONTROL.md) — authentication / authorization / internal-only.
- [**API-CONVENTIONS.md**](./API-CONVENTIONS.md) — quy ước path: `/api/<svc>/public|admin`, `/internal`, `/webhooks`.
- [**outbox-debezium.md**](./outbox-debezium.md) — phát event không mất (outbox + Debezium CDC) + cách mở rộng.

## Thiết kế chuyên sâu (vì sao & cách làm)
- [**saga-purchase-flow.md**](./saga-purchase-flow.md) — luồng mua vé (Order orchestrator + bù trừ).
- [**inventory-no-oversell.md**](./inventory-no-oversell.md) — chống bán trùng (Redis counter / seat hold).
- [**payment-stripe-flow.md**](./payment-stripe-flow.md) — tích hợp Stripe (đa phương thức: thẻ/Konbini/Furikomi, webhook, idempotency).
- [**payment_issue.md**](./payment_issue.md) — danh mục cạm bẫy thanh toán (đọc kèm).
- [**payment-issue-resolutions.md**](./payment-issue-resolutions.md) — ánh xạ TỪNG vấn đề ở trên → cách giải quyết + trạng thái (checklist khi build).
- [**resilience-flash-sale.md**](./resilience-flash-sale.md) — rate limit, circuit breaker, chịu spike on-prem.
- [**waiting-room.md**](./waiting-room.md) — van chặn spike (Redis sorted set + CAPTCHA + admission theo tồn kho).

## Vận hành
- [**deployment-k8s.md**](./deployment-k8s.md) — Ingress, NetworkPolicy (rào `/internal`), Operator, Strimzi.

---

### Lộ trình đọc theo vai trò
- **Mới vào dự án:** dev-runbook → architecture → SECURITY-ACCESS-CONTROL → API-CONVENTIONS.
- **Sắp code lát cắt mua vé:** saga → inventory → payment(+payment_issue) → outbox-debezium.
- **Lên prod / hạ tầng:** deployment-k8s → resilience-flash-sale.
