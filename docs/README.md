# Tài liệu — Nền tảng bán vé sự kiện

Chỉ mục theo thư mục. Ràng buộc gốc (bất biến) ở [`/CLAUDE.md`](../CLAUDE.md).

## `overview/` — bắt đầu
- [**architecture.md**](overview/architecture.md) — tổng quan service, ràng buộc, khuôn chuẩn, quy ước path, chịu tải flash-sale (đọc trước).
- [**dev-runbook.md**](overview/dev-runbook.md) — dựng stack local, cổng/công cụ, smoke-test, debug, seed admin.

## `services/` — thiết kế chi tiết TỪNG service (căn cứ triển khai)
- [**services/README.md**](services/README.md) — index 9 service: DB (DDL), Redis, API, event, invariant, config.

## `standards/` — chuẩn xuyên suốt
- [**API-CONVENTIONS.md**](standards/API-CONVENTIONS.md) — quy ước path: `/api/<svc>/public|admin`, `/internal`, `/webhooks`.
- [**SECURITY-ACCESS-CONTROL.md**](standards/SECURITY-ACCESS-CONTROL.md) — authentication / authorization / internal-only.
- [**jwt-authentication.md**](standards/jwt-authentication.md) — xác thực JWT (RS256, JWKS, verify cục bộ).
- [**outbox-debezium.md**](standards/outbox-debezium.md) — phát event không mất (outbox + Debezium CDC) + cách mở rộng.
- [**resilience-flash-sale.md**](standards/resilience-flash-sale.md) — rate limit, circuit breaker, chịu spike on-prem.

## `flows/` — luồng xuyên service (vì sao & cách làm)
- [**saga-purchase-flow.md**](flows/saga-purchase-flow.md) — luồng mua vé (Order orchestrator + bù trừ).
- [**inventory-no-oversell.md**](flows/inventory-no-oversell.md) — chống bán trùng (Redis counter / seat hold).
- [**payment-stripe-flow.md**](flows/payment-stripe-flow.md) — tích hợp Stripe (thẻ/Konbini/Furikomi, webhook, idempotency).
- [**waiting-room.md**](flows/waiting-room.md) — van chặn spike (Redis sorted set + CAPTCHA + admission theo tồn kho).

## `payment-ref/` — phân tích thanh toán sâu
- [**payment_issue.md**](payment-ref/payment_issue.md) — danh mục cạm bẫy thanh toán (đọc kèm).
- [**payment-issue-resolutions.md**](payment-ref/payment-issue-resolutions.md) — ánh xạ TỪNG vấn đề → cách giải + trạng thái (checklist).

## `impl/` — spec triển khai TỪNG FILE
- [**01-payment-real-stripe.md**](impl/01-payment-real-stripe.md) — thanh toán Stripe thật (card + async Konbini/Furikomi), migration + sửa file + acceptance.

## `ops/` — vận hành
- [**deployment-k8s.md**](ops/deployment-k8s.md) — Ingress, NetworkPolicy (rào `/internal`), Operator, Strimzi.

---

### Lộ trình đọc theo vai trò
- **Mới vào dự án:** `overview/dev-runbook` → `overview/architecture` → `standards/SECURITY-ACCESS-CONTROL` → `standards/API-CONVENTIONS`.
- **Sắp code lát cắt mua vé:** `services/` (03,04,05) → `flows/saga-purchase-flow` → `flows/inventory-no-oversell` → `flows/payment-stripe-flow`(+`payment-ref/`) → `impl/01-payment-real-stripe`.
- **Lên prod / hạ tầng:** `ops/deployment-k8s` → `standards/resilience-flash-sale` → `standards/outbox-debezium`.
