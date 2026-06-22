import { defineStore } from 'pinia'
import { ref } from 'vue'
import { waitingRoomApi } from '../api/waitingRoom'
import type { AdmissionConfig, EventStats } from '../types/waitingRoom'

export const useWaitingRoomStore = defineStore('waitingRoom', () => {
  const config = ref<AdmissionConfig | null>(null)
  const stats = ref<EventStats[]>([])
  const lastRefreshed = ref<Date | null>(null)

  async function fetchConfig() {
    config.value = await waitingRoomApi.getConfig()
    return config.value
  }

  async function updateConfig(p: AdmissionConfig) {
    config.value = await waitingRoomApi.updateConfig(p)
    return config.value
  }

  async function fetchStats() {
    stats.value = await waitingRoomApi.stats()
    lastRefreshed.value = new Date()
    return stats.value
  }

  return { config, stats, lastRefreshed, fetchConfig, updateConfig, fetchStats }
})
