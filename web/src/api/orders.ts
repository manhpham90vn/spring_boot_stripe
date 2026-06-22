import { http } from './client'
import type { OrderResponse, PlaceOrderRequest } from '../types/order'

export const ordersApi = {
  // admissionToken: PASS waiting room (nếu có) — gửi qua header để Order/Gateway verify khi flash sale.
  place: (req: PlaceOrderRequest, admissionToken?: string) =>
    http.post<OrderResponse>(
      '/api/order',
      req,
      admissionToken ? { 'X-Admission-Token': admissionToken } : undefined,
    ),
  get: (id: string) => http.get<OrderResponse>(`/api/order/${id}`),
}
