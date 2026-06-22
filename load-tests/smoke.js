import { sleep, check } from 'k6'
import { baseThresholds } from './config.js'
import { discoverEvent, eventDetail, seats, register, login, getCaptcha } from './lib/api.js'

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: baseThresholds,
}

export function setup() {
  return discoverEvent()
}

export default function (data) {
  eventDetail(data.eventId)
  if (data.seatedTicketTypeId) seats(data.eventId, data.seatedTicketTypeId)

  const email = `smoke_${Date.now()}@loadtest.local`
  const reg = register(email, 'password123')
  check(reg, { 'register 200/201': (r) => r.status === 200 || r.status === 201 })
  login(email, 'password123')

  const { captchaId } = getCaptcha()
  check(null, { 'có captchaId': () => !!captchaId })

  sleep(1)
}
