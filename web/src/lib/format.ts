// Tiện ích định dạng tiền & thời gian.

// priceMinor là đơn vị nhỏ nhất (vd cent/xu). JPY không có phần thập phân → chia 1.
const ZERO_DECIMAL = new Set(['JPY', 'KRW', 'VND'])

export function formatMoney(minor: number, currency: string): string {
  const divisor = ZERO_DECIMAL.has(currency) ? 1 : 100
  const amount = minor / divisor
  try {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency }).format(amount)
  } catch {
    return `${amount.toLocaleString('vi-VN')} ${currency}`
  }
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}
