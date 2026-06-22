import redis from 'k6/x/redis'
import { REDIS_URL } from '../config.js'

const client = new redis.Client(REDIS_URL)

export async function solveCaptcha(captchaId) {
  return client.get(`wr:captcha:${captchaId}`)
}
