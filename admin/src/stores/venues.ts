import { defineStore } from 'pinia'
import { ref } from 'vue'
import { venuesApi } from '../api/venues'
import type { VenuePayload, VenueResponse } from '../types/venues'

/**
 * State địa điểm dùng chung — danh sách venue cần ở nhiều nơi (trang Địa điểm,
 * dropdown khi tạo/sửa Sự kiện). Cache trong store để không fetch lặp.
 */
export const useVenuesStore = defineStore('venues', () => {
  const list = ref<VenueResponse[]>([])
  const loaded = ref(false)

  async function fetchAll() {
    list.value = await venuesApi.list()
    loaded.value = true
    return list.value
  }

  /** Đảm bảo đã có danh sách (chỉ fetch lần đầu) — cho các view chỉ cần dropdown. */
  async function ensureLoaded() {
    if (!loaded.value) await fetchAll()
    return list.value
  }

  async function create(p: VenuePayload) {
    const v = await venuesApi.create(p)
    await fetchAll()
    return v
  }

  async function update(id: string, p: VenuePayload) {
    const v = await venuesApi.update(id, p)
    await fetchAll()
    return v
  }

  async function remove(id: string) {
    await venuesApi.remove(id)
    await fetchAll()
  }

  return { list, loaded, fetchAll, ensureLoaded, create, update, remove }
})
