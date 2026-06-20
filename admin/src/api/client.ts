// HTTP client mỏng cho admin. Mọi request qua gateway dưới /api, gắn Bearer token.

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

interface RequestOptions {
  method?: string
  body?: unknown
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })

  if (res.status === 401) {
    setToken(null)
    throw new ApiError(401, 'Phiên đã hết hạn, đăng nhập lại.')
  }
  if (res.status === 403) {
    throw new ApiError(403, 'Tài khoản không có quyền ADMIN.')
  }
  if (!res.ok) throw new ApiError(res.status, await extractError(res))
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

async function extractError(res: Response): Promise<string> {
  try {
    const data = await res.json()
    return data.message ?? data.error ?? `Lỗi ${res.status}`
  } catch {
    return `Lỗi ${res.status}`
  }
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
}
