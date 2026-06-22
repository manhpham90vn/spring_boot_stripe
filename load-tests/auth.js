import { check, sleep } from 'k6'
import { baseThresholds } from './config.js'
import { register, login } from './lib/api.js'

export const options = {
  scenarios: {
    auth: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 20 },
        { duration: '40s', target: 50 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...baseThresholds,
    'http_req_duration{name:auth_register}': ['p(95)<1500'],
    'http_req_duration{name:auth_login}': ['p(95)<1500'],
  },
}

export default function () {
  const email = `u_${__VU}_${__ITER}_${Date.now()}@loadtest.local`
  const pwd = 'password123'

  const reg = register(email, pwd)
  check(reg, { 'register ok': (r) => r.status === 200 || r.status === 201 })

  const res = login(email, pwd)
  check(res, { 'có accessToken': (r) => !!(r.json() && r.json().accessToken) })

  sleep(1)
}
