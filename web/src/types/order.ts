export type OrderStatus = 'PENDING' | 'AWAITING_PAYMENT' | 'PAID' | 'REJECTED' | 'PAYMENT_FAILED'

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
