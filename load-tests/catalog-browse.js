import { sleep, group } from 'k6'
import { baseThresholds } from './config.js'
import { discoverEvent, listEvents, eventDetail, seats } from './lib/api.js'

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 200 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    ...baseThresholds,
    'http_req_duration{name:catalog_list_events}': ['p(95)<300'],
    'http_req_duration{name:catalog_event_detail}': ['p(95)<300'],
  },
}

export function setup() {
  return discoverEvent()
}

export default function (data) {
  group('browse', () => {
    listEvents()
    sleep(Math.random() * 1)
    eventDetail(data.eventId)
    if (data.seatedTicketTypeId) {
      sleep(Math.random() * 1)
      seats(data.eventId, data.seatedTicketTypeId)
    }
  })
  sleep(1 + Math.random())
}
