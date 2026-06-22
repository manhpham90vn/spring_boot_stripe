import { check, sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import { baseThresholds } from './config.js'
import { discoverEvent, getCaptcha, enqueue, waitingStatus } from './lib/api.js'
import { solveCaptcha } from './lib/captcha.js'

const admitted = new Counter('wr_admitted')
const soldOut = new Counter('wr_soldout')
const enqueueFailed = new Counter('wr_enqueue_failed')
const timeToAdmit = new Trend('wr_time_to_admit_ms', true)

export const options = {
  scenarios: {
    queue: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '20s', target: 20 },
        { duration: '40s', target: 50 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...baseThresholds,
    'http_req_duration{name:wr_enqueue}': ['p(95)<500'],
    'http_req_duration{name:wr_status}': ['p(95)<300'],
  },
}

export function setup() {
  return discoverEvent()
}

export default async function (data) {
  const { captchaId } = getCaptcha()
  if (!captchaId) {
    enqueueFailed.add(1)
    return
  }

  const answer = await solveCaptcha(captchaId)
  const enq = enqueue(data.eventId, captchaId, answer)
  if (enq.status !== 200) {
    enqueueFailed.add(1)
    return
  }

  const token = enq.json('token')
  const start = Date.now()

  for (let i = 0; i < 15; i++) {
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
