import { getBlobWithHeaders, http } from './client'
import type { EnqueueResponse, WaitingStatusResponse } from '../types/waitingroom'

// Waiting Room: van chặn spike. CAPTCHA → xếp hàng → poll vị trí → nhận PASS.
export const waitingRoomApi = {
  // Lấy ảnh CAPTCHA; trả captchaId (header X-Captcha-Id) + objectURL để hiển thị <img>.
  async captcha(): Promise<{ captchaId: string; imageUrl: string }> {
    const { blob, headers } = await getBlobWithHeaders('/api/waitingroom/public/captcha')
    // axios chuẩn hoá tên header về chữ thường.
    const captchaId = (headers['x-captcha-id'] as string | undefined) ?? ''
    return { captchaId, imageUrl: URL.createObjectURL(blob) }
  },
  enqueue: (eventId: string, body: { captchaId: string; captchaAnswer: string }) =>
    http.post<EnqueueResponse>(`/api/waitingroom/public/${eventId}/enqueue`, body),
  status: (eventId: string, token: string) =>
    http.get<WaitingStatusResponse>(
      `/api/waitingroom/public/${eventId}/status?token=${encodeURIComponent(token)}`,
    ),
}
