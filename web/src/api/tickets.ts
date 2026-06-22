import { http } from './client'
import type { TicketResponse } from '../types/ticket'

export const ticketsApi = {
  mine: () => http.get<TicketResponse[]>('/api/ticket'),
  // Ảnh QR cần header Authorization → tải qua fetch rồi tạo object URL (xem TicketCard).
  qrUrl: (id: string) => `/api/ticket/${id}/qr.png`,
}
