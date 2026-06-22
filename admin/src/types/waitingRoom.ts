// Cấu hình admission động của Waiting Room (rate = số người thả/giây; TTL tính bằng giây).
export interface AdmissionConfig {
  rate: number
  tokenTtlSeconds: number
  admitTtlSeconds: number
}

// Thống kê hàng chờ 1 event (ảnh chụp tại thời điểm gọi). Phản chiếu EventStats backend.
export interface EventStats {
  eventId: string
  waiting: number // số người đang chờ
  admittedTotal: number // tổng đã được thả vào (cộng dồn)
  ratePerSecond: number // nhịp thả hiệu lực
  etaSeconds: number // thời gian dự kiến rút cạn hàng
  soldOut: boolean
}
