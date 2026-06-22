import { http } from './client'
import type { AdmissionConfig, EventStats } from '../types/waitingRoom'

// Waiting Room: cấu hình admission (lưu Redis, áp dụng ngay) + thống kê hàng chờ.
export const waitingRoomApi = {
  getConfig: () => http.get<AdmissionConfig>('/api/waitingroom/admin/config'),
  updateConfig: (p: AdmissionConfig) =>
    http.put<AdmissionConfig>('/api/waitingroom/admin/config', p),
  stats: () => http.get<EventStats[]>('/api/waitingroom/admin/stats'),
  eventStats: (eventId: string) =>
    http.get<EventStats>(`/api/waitingroom/admin/stats/${eventId}`),
}
