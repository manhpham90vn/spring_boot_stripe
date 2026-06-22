import { Counter } from 'k6/metrics'

export const rateLimited = new Counter('rate_limited')
