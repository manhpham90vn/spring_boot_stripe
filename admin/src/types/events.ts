import type { VenueResponse } from './venues'

export type EventStatus = 'DRAFT' | 'ON_SALE' | 'CLOSED' | 'CANCELLED'
export type TicketTypeKind = 'GA' | 'SEATED'

export interface TicketTypeResponse {
  id: string
  name: string
  description: string | null
  kind: TicketTypeKind
  priceMinor: number
  currency: string
  maxPerOrder: number
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

// ---- Request payloads ----
export interface EventPayload {
  venueId: string
  title: string
  description: string
  startsAt: string // ISO instant
  salesStartAt: string | null
  salesEndAt: string | null
}

// Một ghế khi tạo loại vé SEATED (đi vào seat_map). Đồng bộ với catalog SeatInput.
export interface SeatInput {
  section: string
  rowLabel: string
  seatNumber: string
}

export interface TicketTypePayload {
  name: string
  description: string
  // null/'GA' → vé tự do (cần totalQty). 'SEATED' → ghế ngồi (cần seats; tồn = số ghế).
  kind?: TicketTypeKind
  priceMinor: number
  currency: string
  maxPerOrder: number
  // GA: tổng tồn kho (Catalog chuyển sang Inventory để seed). SEATED: bỏ trống.
  totalQty?: number
  // SEATED: danh sách ghế (bắt buộc khi tạo mới; bất biến khi sửa).
  seats?: SeatInput[]
}
