// Các hàm gọi API theo nghiệp vụ. Path khớp quy ước /api/<service>/... của gateway.
import { http, setToken } from './client'
import type {
  EventDetailResponse,
  EventSummaryResponse,
  OrderResponse,
  PlaceOrderRequest,
  SeatResponse,
  TicketResponse,
  TokenResponse,
  UserResponse,
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
  place: (req: PlaceOrderRequest) => http.post<OrderResponse>('/api/order', req),
  get: (id: string) => http.get<OrderResponse>(`/api/order/${id}`),
}

export const tickets = {
  mine: () => http.get<TicketResponse[]>('/api/ticket'),
  // Ảnh QR cần header Authorization → tải qua fetch rồi tạo object URL (xem TicketCard).
  qrUrl: (id: string) => `/api/ticket/${id}/qr.png`,
}
