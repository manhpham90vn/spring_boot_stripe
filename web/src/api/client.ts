// HTTP client trên axios. Mọi request đi qua API Gateway dưới tiền tố /api.
// Interceptor: gắn Bearer token; chuẩn hoá lỗi HTTP thành ApiError để UI bắt.
import axios, { type AxiosError, type AxiosResponseHeaders } from 'axios'

const TOKEN_KEY = 'tickethub.token'

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

// Gắn token (nếu có) cho mọi request.
api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Chuẩn hoá lỗi → ApiError. 401 → xoá token (phiên hết hạn).
api.interceptors.response.use(
  (res) => res,
  (error: AxiosError<{ message?: string; error?: string }>) => {
    const res = error.response
    if (!res) {
      return Promise.reject(new ApiError(0, 'Không kết nối được máy chủ.'))
    }
    if (res.status === 401) {
      setToken(null)
      return Promise.reject(new ApiError(401, 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.'))
    }
    const msg = res.data?.message ?? res.data?.error ?? `Lỗi ${res.status}`
    return Promise.reject(new ApiError(res.status, msg))
  },
)

/** Tải nhị phân (vd ảnh CAPTCHA) kèm header phản hồi. */
export async function getBlobWithHeaders(
  path: string,
): Promise<{ blob: Blob; headers: AxiosResponseHeaders }> {
  const res = await api.get<Blob>(path, { responseType: 'blob' })
  return { blob: res.data, headers: res.headers as AxiosResponseHeaders }
}

export const http = {
  get: <T>(path: string) => api.get<T>(path).then((r) => r.data),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    api.post<T>(path, body, { headers }).then((r) => r.data),
}
