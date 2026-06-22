// HTTP client mỏng quanh fetch. Mọi request đi qua API Gateway dưới tiền tố /api.
// Token JWT (nếu có) được gắn header Authorization. Lỗi HTTP ném ApiError để UI bắt.

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

interface RequestOptions {
  method?: string
  body?: unknown
  auth?: boolean // có gắn Bearer token không (mặc định: gắn nếu có token)
  headers?: Record<string, string> // header bổ sung (vd PASS waiting room)
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { ...opts.headers }
  const token = getToken()
  if (token && opts.auth !== false) headers['Authorization'] = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })

  if (res.status === 401) {
    setToken(null)
    throw new ApiError(401, 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.')
  }
  if (!res.ok) {
    throw new ApiError(res.status, await extractError(res))
  }
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

/** Tải nhị phân (vd ảnh CAPTCHA) kèm header phản hồi — fetch trả cả blob lẫn header. */
export async function getBlobWithHeaders(
  path: string,
): Promise<{ blob: Blob; headers: Headers }> {
  const res = await fetch(path)
  if (!res.ok) throw new ApiError(res.status, await extractError(res))
  return { blob: await res.blob(), headers: res.headers }
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: 'POST', body, headers }),
}
