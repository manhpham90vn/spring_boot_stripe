import { http } from './client'
import type {
  EventDetailResponse,
  EventPayload,
  EventStatus,
  EventSummaryResponse,
  TicketTypePayload,
  TicketTypeResponse,
} from '../types/events'

const ADMIN = '/api/catalog/admin'

export const eventsApi = {
  list: () => http.get<EventSummaryResponse[]>(`${ADMIN}/events`),
  detail: (id: string) => http.get<EventDetailResponse>(`/api/catalog/public/events/${id}`),
  create: (p: EventPayload) => http.post<EventDetailResponse>(`${ADMIN}/events`, p),
  update: (id: string, p: EventPayload) =>
    http.put<EventDetailResponse>(`${ADMIN}/events/${id}`, p),
  changeStatus: (id: string, status: EventStatus) =>
    http.put<EventDetailResponse>(`${ADMIN}/events/${id}/status`, { status }),
  remove: (id: string) => http.del(`${ADMIN}/events/${id}`),
}

export const ticketTypesApi = {
  add: (eventId: string, p: TicketTypePayload) =>
    http.post<TicketTypeResponse>(`${ADMIN}/events/${eventId}/ticket-types`, p),
  update: (eventId: string, ttId: string, p: TicketTypePayload) =>
    http.put<TicketTypeResponse>(`${ADMIN}/events/${eventId}/ticket-types/${ttId}`, p),
  remove: (eventId: string, ttId: string) =>
    http.del(`${ADMIN}/events/${eventId}/ticket-types/${ttId}`),
}
