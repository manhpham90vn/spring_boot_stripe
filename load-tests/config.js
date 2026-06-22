import http from 'k6/http'

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
export const REDIS_URL = __ENV.REDIS_URL || 'redis://localhost:6379'

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 429))

export const SPOOF_IP = (__ENV.SPOOF_IP || '') !== ''

function randByte(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1))
}

export function withClientIp(headers) {
  if (!SPOOF_IP) return headers
  const ip = `${randByte(1, 223)}.${randByte(0, 255)}.${randByte(0, 255)}.${randByte(1, 254)}`
  return Object.assign({ 'X-Forwarded-For': ip }, headers)
}

export const baseThresholds = {
  http_req_failed: ['rate<0.01'], // < 1% request lỗi
  http_req_duration: ['p(95)<800'], // p95 < 800ms
}

export const JSON_HEADERS = { 'Content-Type': 'application/json' }
