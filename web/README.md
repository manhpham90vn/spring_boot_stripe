# web — Storefront mua vé (TicketHub)

Giao diện khách hàng cho hệ thống bán vé sự kiện. **React 19 + Vite + TypeScript.**

## Luồng người dùng
- **Trang chủ** (`/`): danh sách sự kiện đang mở bán — `GET /api/catalog/public/events`.
- **Chi tiết sự kiện** (`/events/:id`): mô tả + các hạng vé, chọn hạng & số lượng.
- **Đăng nhập / đăng ký** (`/login`): `POST /api/auth/public/login` · `/register`.
- **Đặt vé**: `POST /api/order` chạy saga đồng bộ → trả về đơn ở trạng thái cuối
  (`PAID` / `REJECTED` / `PAYMENT_FAILED`), UI hiển thị kết quả tương ứng.
- **Vé của tôi** (`/tickets`, cần đăng nhập): `GET /api/ticket`, mỗi vé hiển thị
  mã QR tải từ `GET /api/ticket/{id}/qr.png` (kèm Bearer token).

## Kiến trúc client
- `src/api/` — `client.ts` (fetch wrapper gắn JWT, bắt 401/lỗi), `endpoints.ts`
  (hàm gọi theo nghiệp vụ), `types.ts` (kiểu phản chiếu DTO backend).
- `src/auth/AuthContext.tsx` — lưu token ở `localStorage`, khôi phục phiên qua `/api/auth/me`.
- `src/pages/` — các trang; `src/components/` — Layout (nav) + RequireAuth (route bảo vệ).

Mọi request đi qua **API Gateway** dưới tiền tố `/api`. Dev server proxy `/api` →
`http://localhost:8080` (đổi bằng biến môi trường `API_TARGET`).

## Chạy
```bash
pnpm install
pnpm dev        # http://localhost:5173  (proxy /api -> gateway:8080)
pnpm build      # tsc -b && vite build  -> dist/
pnpm preview
```
Đổi đích gateway: `API_TARGET=http://gateway.local:8080 pnpm dev`.
