import { ApiError } from '../api/client'

// Chuẩn hoá lỗi bất kỳ → message hiển thị được. Thay mẫu lặp:
//   e instanceof ApiError ? e.message : '...'
export function errorMessage(e: unknown, fallback = 'Đã có lỗi xảy ra.'): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error && e.message) return e.message
  return fallback
}
