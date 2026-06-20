import { http, setToken } from './client'
import type {
  EventDetailResponse,
  EventPayload,
  EventStatus,
  EventSummaryResponse,
  TicketTypePayload,
  TicketTypeResponse,
  TokenResponse,
  UserResponse,
  VenuePayload,
  VenueResponse,
} from './types'

export const auth = {
  async login(email: string, password: string): Promise<UserResponse> {
    const res = await http.post<TokenResponse>('/api/auth/public/login', { email, password })
    setToken(res.accessToken)
    return http.get<UserResponse>('/api/auth/me')
  },
  me: () => http.get<UserResponse>('/api/auth/me'),
}

const ADMIN = '/api/catalog/admin'

export const venues = {
  list: () => http.get<VenueResponse[]>('/api/catalog/public/venues'),
  create: (p: VenuePayload) => http.post<VenueResponse>(`${ADMIN}/venues`, p),
  update: (id: string, p: VenuePayload) => http.put<VenueResponse>(`${ADMIN}/venues/${id}`, p),
  remove: (id: string) => http.del(`${ADMIN}/venues/${id}`),
}

export const events = {
  list: () => http.get<EventSummaryResponse[]>(`${ADMIN}/events`),
  detail: (id: string) => http.get<EventDetailResponse>(`/api/catalog/public/events/${id}`),
  create: (p: EventPayload) => http.post<EventDetailResponse>(`${ADMIN}/events`, p),
  update: (id: string, p: EventPayload) =>
    http.put<EventDetailResponse>(`${ADMIN}/events/${id}`, p),
  changeStatus: (id: string, status: EventStatus) =>
    http.put<EventDetailResponse>(`${ADMIN}/events/${id}/status`, { status }),
  remove: (id: string) => http.del(`${ADMIN}/events/${id}`),
}

export const ticketTypes = {
  add: (eventId: string, p: TicketTypePayload) =>
    http.post<TicketTypeResponse>(`${ADMIN}/events/${eventId}/ticket-types`, p),
  update: (eventId: string, ttId: string, p: TicketTypePayload) =>
    http.put<TicketTypeResponse>(`${ADMIN}/events/${eventId}/ticket-types/${ttId}`, p),
  remove: (eventId: string, ttId: string) =>
    http.del(`${ADMIN}/events/${eventId}/ticket-types/${ttId}`),
}
