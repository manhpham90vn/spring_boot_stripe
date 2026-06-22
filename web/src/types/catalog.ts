// Catalog: sự kiện, địa điểm, hạng vé, sơ đồ ghế. Tiền luôn là *Minor* (đơn vị nhỏ nhất).
export type EventStatus = 'DRAFT' | 'ON_SALE' | 'CLOSED' | 'CANCELLED'
export type TicketTypeKind = 'GA' | 'SEATED'
export type SeatAvailability = 'AVAILABLE' | 'HELD' | 'SOLD'

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
