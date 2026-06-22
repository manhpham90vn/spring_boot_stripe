// HTTP client trên axios. Mọi request qua gateway dưới /api, gắn Bearer token.
// Interceptor chuẩn hoá lỗi HTTP thành ApiError để UI bắt.
import axios, { type AxiosError } from 'axios'

const TOKEN_KEY = 'tickethub.admin.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const api = axios.create()

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (error: AxiosError<{ message?: string; error?: string }>) => {
    const res = error.response
    if (!res) {
      return Promise.reject(new ApiError(0, 'Không kết nối được máy chủ.'))
    }
    if (res.status === 401) {
      setToken(null)
      return Promise.reject(new ApiError(401, 'Phiên đã hết hạn, đăng nhập lại.'))
    }
    if (res.status === 403) {
      return Promise.reject(new ApiError(403, 'Tài khoản không có quyền ADMIN.'))
    }
    const msg = res.data?.message ?? res.data?.error ?? `Lỗi ${res.status}`
    return Promise.reject(new ApiError(res.status, msg))
  },
)

export const http = {
  get: <T>(path: string) => api.get<T>(path).then((r) => r.data),
  post: <T>(path: string, body?: unknown) => api.post<T>(path, body).then((r) => r.data),
  put: <T>(path: string, body?: unknown) => api.put<T>(path, body).then((r) => r.data),
  del: (path: string) => api.delete(path).then(() => undefined),
}
