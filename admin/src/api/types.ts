// Kiểu phản chiếu DTO backend (catalog + auth). Đồng bộ thủ công.

export type EventStatus = 'DRAFT' | 'ON_SALE' | 'CLOSED' | 'CANCELLED'

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
export interface VenuePayload {
  name: string
  address: string
  city: string
}

export interface EventPayload {
  venueId: string
  title: string
  description: string
  startsAt: string // ISO instant
  salesStartAt: string | null
  salesEndAt: string | null
}

export interface TicketTypePayload {
  name: string
  description: string
  priceMinor: number
  currency: string
  maxPerOrder: number
}
