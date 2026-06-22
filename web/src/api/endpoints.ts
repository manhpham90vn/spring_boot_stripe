// Các hàm gọi API theo nghiệp vụ. Path khớp quy ước /api/<service>/... của gateway.
import { getBlobWithHeaders, http, setToken } from './client'
import type {
  EnqueueResponse,
  EventDetailResponse,
  EventSummaryResponse,
  OrderResponse,
  PlaceOrderRequest,
  SeatResponse,
  TicketResponse,
  TokenResponse,
  UserResponse,
  WaitingStatusResponse,
} from './types'

export const auth = {
  async login(email: string, password: string): Promise<TokenResponse> {
    const res = await http.post<TokenResponse>('/api/auth/public/login', { email, password })
    setToken(res.accessToken)
    return res
  },
  async register(email: string, password: string): Promise<UserResponse> {
    return http.post<UserResponse>('/api/auth/public/register', { email, password })
  },
  me: () => http.get<UserResponse>('/api/auth/me'),
}

export const catalog = {
  listEvents: () => http.get<EventSummaryResponse[]>('/api/catalog/public/events'),
  eventDetail: (id: string) => http.get<EventDetailResponse>(`/api/catalog/public/events/${id}`),
  // Sơ đồ ghế của loại vé SEATED, kèm trạng thái AVAILABLE/HELD/SOLD (gộp từ Inventory).
  seats: (eventId: string, ticketTypeId: string) =>
    http.get<SeatResponse[]>(
      `/api/catalog/public/events/${eventId}/ticket-types/${ticketTypeId}/seats`,
    ),
}

export const orders = {
  // admissionToken: PASS waiting room (nếu có) — gửi qua header để Order/Gateway verify khi flash sale.
  place: (req: PlaceOrderRequest, admissionToken?: string) =>
    http.post<OrderResponse>(
      '/api/order',
      req,
      admissionToken ? { 'X-Admission-Token': admissionToken } : undefined,
    ),
  get: (id: string) => http.get<OrderResponse>(`/api/order/${id}`),
}

// Waiting Room: van chặn spike. CAPTCHA → xếp hàng → poll vị trí → nhận PASS.
export const waitingroom = {
  // Lấy ảnh CAPTCHA; trả captchaId (header X-Captcha-Id) + objectURL để hiển thị <img>.
  async captcha(): Promise<{ captchaId: string; imageUrl: string }> {
    const { blob, headers } = await getBlobWithHeaders('/api/waitingroom/public/captcha')
    return { captchaId: headers.get('X-Captcha-Id') ?? '', imageUrl: URL.createObjectURL(blob) }
  },
  enqueue: (eventId: string, body: { captchaId: string; captchaAnswer: string }) =>
    http.post<EnqueueResponse>(`/api/waitingroom/public/${eventId}/enqueue`, body),
  status: (eventId: string, token: string) =>
    http.get<WaitingStatusResponse>(
      `/api/waitingroom/public/${eventId}/status?token=${encodeURIComponent(token)}`,
    ),
}

export const tickets = {
  mine: () => http.get<TicketResponse[]>('/api/ticket'),
  // Ảnh QR cần header Authorization → tải qua fetch rồi tạo object URL (xem TicketCard).
  qrUrl: (id: string) => `/api/ticket/${id}/qr.png`,
}
