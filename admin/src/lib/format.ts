const ZERO_DECIMAL = new Set(['JPY', 'KRW', 'VND'])

export function formatMoney(minor: number, currency: string): string {
  const divisor = ZERO_DECIMAL.has(currency) ? 1 : 100
  try {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency }).format(minor / divisor)
  } catch {
    return `${(minor / divisor).toLocaleString('vi-VN')} ${currency}`
  }
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('vi-VN', { dateStyle: 'medium', timeStyle: 'short' })
}

// <input type="datetime-local"> ↔ ISO instant.
export function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  const off = d.getTimezoneOffset() * 60000
  return new Date(d.getTime() - off).toISOString().slice(0, 16)
}

export function localInputToIso(local: string): string | null {
  if (!local) return null
  return new Date(local).toISOString()
}

// ETA giây → "Mm Ss" cho dễ đọc (Waiting Room).
export function formatDuration(sec: number): string {
  if (sec <= 0) return '—'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}
