import { defineStore } from 'pinia'
import { ref } from 'vue'
import { eventsApi, ticketTypesApi } from '../api/events'
import type {
  EventDetailResponse,
  EventPayload,
  EventStatus,
  EventSummaryResponse,
  TicketTypePayload,
} from '../types/events'

export const useEventsStore = defineStore('events', () => {
  const list = ref<EventSummaryResponse[]>([])
  const current = ref<EventDetailResponse | null>(null)

  async function fetchList() {
    list.value = await eventsApi.list()
    return list.value
  }

  async function fetchDetail(id: string) {
    const detail = await eventsApi.detail(id)
    current.value = detail
    return detail
  }

  async function create(p: EventPayload) {
    return eventsApi.create(p)
  }

  async function update(id: string, p: EventPayload) {
    current.value = await eventsApi.update(id, p)
    return current.value
  }

  async function changeStatus(id: string, status: EventStatus) {
    current.value = await eventsApi.changeStatus(id, status)
    return current.value
  }

  async function remove(id: string) {
    await eventsApi.remove(id)
  }

  // --- Hạng vé: mutation xong refetch detail để cập nhật current.ticketTypes ---
  async function addTicketType(eventId: string, p: TicketTypePayload) {
    await ticketTypesApi.add(eventId, p)
    await fetchDetail(eventId)
  }

  async function updateTicketType(eventId: string, ttId: string, p: TicketTypePayload) {
    await ticketTypesApi.update(eventId, ttId, p)
    await fetchDetail(eventId)
  }

  async function removeTicketType(eventId: string, ttId: string) {
    await ticketTypesApi.remove(eventId, ttId)
    await fetchDetail(eventId)
  }

  return {
    list,
    current,
    fetchList,
    fetchDetail,
    create,
    update,
    changeStatus,
    remove,
    addTicketType,
    updateTicketType,
    removeTicketType,
  }
})
