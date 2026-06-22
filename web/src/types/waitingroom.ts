// Waiting Room (van chặn spike flash sale).
export interface EnqueueResponse {
  token: string // định danh chỗ trong hàng (poll /status bằng token này)
  position: number // vị trí 1-based
  etaSeconds: number // ước lượng thời gian chờ
}

export interface WaitingStatusResponse {
  position: number
  admitted: boolean
  accessToken: string | null // PASS — gửi kèm khi đặt đơn (header X-Admission-Token)
  soldOut: boolean
}
