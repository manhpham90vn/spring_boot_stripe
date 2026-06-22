import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/auth'

// Bọc các route cần đăng nhập. Chưa có user → đẩy sang /login, nhớ điểm đến để quay lại.
export default function RequireAuth({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user)
  const loading = useAuthStore((s) => s.loading)
  const location = useLocation()

  if (loading) return <div className="spinner">Đang tải…</div>
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />
  return <>{children}</>
}
