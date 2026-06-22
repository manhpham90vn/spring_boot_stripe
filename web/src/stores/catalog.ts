import { create } from 'zustand'
import { catalogApi } from '../api/catalog'
import type { EventDetailResponse, EventSummaryResponse } from '../types/catalog'

interface CatalogState {
  events: EventSummaryResponse[]
  currentEvent: EventDetailResponse | null
  fetchEvents: () => Promise<EventSummaryResponse[]>
  fetchEvent: (id: string) => Promise<EventDetailResponse>
}

// State catalog dùng chung — danh sách sự kiện (cache cho trang Sự kiện) + chi tiết đang xem.
export const useCatalogStore = create<CatalogState>((set) => ({
  events: [],
  currentEvent: null,

  async fetchEvents() {
    const events = await catalogApi.listEvents()
    set({ events })
    return events
  },

  async fetchEvent(id) {
    const event = await catalogApi.eventDetail(id)
    set({ currentEvent: event })
    return event
  },
}))
