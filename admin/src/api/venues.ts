import { http } from './client'
import type { VenuePayload, VenueResponse } from '../types/venues'

const ADMIN = '/api/catalog/admin'

export const venuesApi = {
  list: () => http.get<VenueResponse[]>('/api/catalog/public/venues'),
  create: (p: VenuePayload) => http.post<VenueResponse>(`${ADMIN}/venues`, p),
  update: (id: string, p: VenuePayload) => http.put<VenueResponse>(`${ADMIN}/venues/${id}`, p),
  remove: (id: string) => http.del(`${ADMIN}/venues/${id}`),
}
