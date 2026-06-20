# admin — Bảng quản trị danh mục (TicketHub)

Giao diện quản trị cho hệ thống bán vé. **Vue 3 `<script setup>` + Vite + TypeScript.**
Serve dưới đường dẫn con `/admin/` (xem `vite.config.ts` → `base`).

## Chức năng
- **Đăng nhập** (`/login`): dùng chung Auth service; chỉ tài khoản role **ADMIN**
  mới vào được (kiểm tra `/api/auth/me` sau khi đăng nhập).
- **Địa điểm** (`/venues`): liệt kê + tạo/sửa/xoá venue
  (`/api/catalog/admin/venues`, đọc qua `/api/catalog/public/venues`).
- **Sự kiện** (`/events`): liệt kê **mọi** sự kiện gồm cả `DRAFT`
  (`GET /api/catalog/admin/events`); tạo sự kiện mới.
- **Chi tiết sự kiện** (`/events/:id`): sửa thông tin, đổi trạng thái
  (`DRAFT → ON_SALE → CLOSED/CANCELLED`), và CRUD **hạng vé** (ticket type).

> Phân quyền: gateway chỉ yêu cầu "có JWT"; ràng buộc role `ADMIN` do catalog
> service tự kiểm (`common-security`). UI bắt 403 và báo lỗi rõ ràng.

## Kiến trúc client
- `src/api/` — `client.ts` (fetch wrapper gắn JWT, bắt 401/403), `endpoints.ts`
  (venues / events / ticketTypes), `types.ts` (kiểu phản chiếu DTO).
- `src/stores/auth.ts` — store đăng nhập (reactive singleton, lưu token localStorage).
- `src/router/` — vue-router với guard chặn route cần đăng nhập.
- `src/views/` — Login, Venues, Events, EventDetail.

Tiền hiển thị theo *minor unit* (đơn vị nhỏ nhất): VND/JPY/KRW không chia, còn lại chia 100.
Mọi request qua **API Gateway** `/api`; dev proxy → `http://localhost:8080` (`API_TARGET`).

## Chạy
```bash
pnpm install
pnpm dev        # http://localhost:5173/admin/
pnpm build      # vue-tsc -b && vite build -> dist/
pnpm preview
```
