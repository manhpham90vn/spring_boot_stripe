import { sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import { baseThresholds } from './config.js'
import { discoverEvent, listEvents, eventDetail, getCaptcha, enqueue, waitingStatus } from './lib/api.js'
import { solveCaptcha } from './lib/captcha.js'

const admitted = new Counter('wr_admitted')
const soldOut = new Counter('wr_soldout')
const timeToAdmit = new Trend('wr_time_to_admit_ms', true)

export const options = {
  scenarios: {
    browsers: {
      executor: 'ramping-vus',
      exec: 'browse',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 150 },
        { duration: '1m', target: 300 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    buyers: {
      executor: 'ramping-arrival-rate',
      exec: 'enterQueue',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 800,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...baseThresholds,
    'http_req_duration{name:catalog_event_detail}': ['p(95)<400'],
    'http_req_duration{name:wr_enqueue}': ['p(95)<600'],
  },
}

export function setup() {
  return discoverEvent()
}

// exec: lướt
export function browse(data) {
  listEvents()
  sleep(1)
  eventDetail(data.eventId)
  sleep(1 + Math.random())
}

// exec: vào hàng chờ
export async function enterQueue(data) {
  const { captchaId } = getCaptcha()
  if (!captchaId) return
  const answer = await solveCaptcha(captchaId)
  const enq = enqueue(data.eventId, captchaId, answer)
  if (enq.status !== 200) return

  const token = enq.json('token')
  const start = Date.now()
  for (let i = 0; i < 10; i++) {
    const res = waitingStatus(data.eventId, token)
    if (res.status !== 200) break
    const body = res.json()
    if (body.admitted) {
      admitted.add(1)
      timeToAdmit.add(Date.now() - start)
      break
    }
    if (body.soldOut) {
      soldOut.add(1)
      break
    }
    sleep(2)
  }
}
