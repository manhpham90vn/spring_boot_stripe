import http from 'k6/http'
import { check } from 'k6'
import { BASE_URL, JSON_HEADERS, withClientIp } from '../config.js'
import { rateLimited } from './metrics.js'

function get(name) {
  return { tags: { name }, headers: withClientIp({}) }
}
function post(name) {
  return { tags: { name }, headers: withClientIp(JSON_HEADERS) }
}

function checkOk(res, name) {
  if (res.status === 429) rateLimited.add(1)
  check(res, { [`${name} ok (2xx/429)`]: (r) => (r.status >= 200 && r.status < 300) || r.status === 429 })
  return res
}

export function listEvents() {
  return checkOk(http.get(`${BASE_URL}/api/catalog/public/events`, get('catalog_list_events')), 'list events')
}

export function eventDetail(id) {
  return checkOk(http.get(`${BASE_URL}/api/catalog/public/events/${id}`, get('catalog_event_detail')), 'event detail')
}

export function seats(eventId, ticketTypeId) {
  return checkOk(
    http.get(`${BASE_URL}/api/catalog/public/events/${eventId}/ticket-types/${ticketTypeId}/seats`, get('catalog_seats')),
    'seats',
  )
}

export function register(email, password) {
  return checkOk(
    http.post(`${BASE_URL}/api/auth/public/register`, JSON.stringify({ email, password }), post('auth_register')),
    'register',
  )
}

export function login(email, password) {
  return checkOk(
    http.post(`${BASE_URL}/api/auth/public/login`, JSON.stringify({ email, password }), post('auth_login')),
    'login',
  )
}

export function getCaptcha() {
  const res = checkOk(http.get(`${BASE_URL}/api/waitingroom/public/captcha`, get('wr_captcha')), 'captcha')
  return { res, captchaId: res.headers['X-Captcha-Id'] }
}

export function enqueue(eventId, captchaId, captchaAnswer) {
  return checkOk(
    http.post(`${BASE_URL}/api/waitingroom/public/${eventId}/enqueue`, JSON.stringify({ captchaId, captchaAnswer }), post('wr_enqueue')),
    'enqueue',
  )
}

export function waitingStatus(eventId, token) {
  return checkOk(
    http.get(`${BASE_URL}/api/waitingroom/public/${eventId}/status?token=${encodeURIComponent(token)}`, get('wr_status')),
    'status',
  )
}

export function discoverEvent() {
  const events = listEvents().json()
  const onSale = events.find((e) => e.status === 'ON_SALE') || events[0]
  if (!onSale) throw new Error('Không có sự kiện nào — hãy seed dữ liệu trước khi chạy load test.')

  const detail = eventDetail(onSale.id).json()
  const ga = detail.ticketTypes.find((t) => t.kind === 'GA')
  const seated = detail.ticketTypes.find((t) => t.kind === 'SEATED')
  return {
    eventId: onSale.id,
    gaTicketTypeId: ga ? ga.id : null,
    seatedTicketTypeId: seated ? seated.id : null,
  }
}
