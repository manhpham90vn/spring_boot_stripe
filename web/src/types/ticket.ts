export type TicketStatus = 'VALID' | 'USED'

export interface TicketResponse {
  id: string
  orderId: string
  eventId: string
  ticketTypeId: string
  status: TicketStatus
  qrToken: string
  issuedAt: string
}
