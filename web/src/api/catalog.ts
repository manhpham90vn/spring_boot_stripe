import { http } from './client'
import type { EventDetailResponse, EventSummaryResponse, SeatResponse } from '../types/catalog'

export const catalogApi = {
  listEvents: () => http.get<EventSummaryResponse[]>('/api/catalog/public/events'),
  eventDetail: (id: string) => http.get<EventDetailResponse>(`/api/catalog/public/events/${id}`),
  // Sơ đồ ghế của loại vé SEATED, kèm trạng thái AVAILABLE/HELD/SOLD (gộp từ Inventory).
  seats: (eventId: string, ticketTypeId: string) =>
    http.get<SeatResponse[]>(
      `/api/catalog/public/events/${eventId}/ticket-types/${ticketTypeId}/seats`,
    ),
}
