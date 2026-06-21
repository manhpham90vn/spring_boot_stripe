// Kiểu dữ liệu phản chiếu các DTO của backend (Java records). Giữ đồng bộ thủ công
// với catalog/order/ticket/auth service. Tiền luôn là *Minor* (đơn vị nhỏ nhất, vd cent).

export type EventStatus = 'DRAFT' | 'ON_SALE' | 'CLOSED' | 'CANCELLED'
export type OrderStatus =
  | 'PENDING'
  | 'AWAITING_PAYMENT'
  | 'PAID'
  | 'REJECTED'
  | 'PAYMENT_FAILED'
export type TicketStatus = 'VALID' | 'USED'
export type TicketTypeKind = 'GA' | 'SEATED'
export type SeatAvailability = 'AVAILABLE' | 'HELD' | 'SOLD'

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface UserResponse {
  id: string
  email: string
  role: string
}

export interface VenueResponse {
  id: string
  name: string
  address: string | null
  city: string | null
}

export interface TicketTypeResponse {
  id: string
  name: string
  description: string | null
  kind: TicketTypeKind
  priceMinor: number
  currency: string
  maxPerOrder: number
}

export interface SeatResponse {
  seatId: string
  section: string | null
  rowLabel: string | null
  seatNumber: string | null
  label: string
  status: SeatAvailability
}

export interface EventSummaryResponse {
  id: string
  title: string
  status: EventStatus
  startsAt: string
  venue: VenueResponse | null
}

export interface EventDetailResponse {
  id: string
  title: string
  description: string | null
  status: EventStatus
  startsAt: string
  salesStartAt: string | null
  salesEndAt: string | null
  venue: VenueResponse | null
  ticketTypes: TicketTypeResponse[]
}

export interface PlaceOrderRequest {
  eventId: string
  ticketTypeId: string
  // GA: gửi quantity (>=1). SEATED: gửi seatIds (danh sách ghế chọn).
  quantity?: number
  seatIds?: string[]
}

export interface OrderResponse {
  id: string
  status: OrderStatus
  eventId: string
  ticketTypeId: string
  quantity: number
  amountMinor: number
  currency: string
  paymentId: string | null
  // Chỉ có ở response của POST /api/order — dùng để xác nhận Payment Element. GET trả null.
  clientSecret: string | null
  failureReason: string | null
}

export interface TicketResponse {
  id: string
  orderId: string
  eventId: string
  ticketTypeId: string
  status: TicketStatus
  qrToken: string
  issuedAt: string
}
