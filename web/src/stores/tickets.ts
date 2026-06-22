import { create } from 'zustand'
import { ticketsApi } from '../api/tickets'
import type { TicketResponse } from '../types/ticket'

interface TicketsState {
  tickets: TicketResponse[]
  fetchMine: () => Promise<TicketResponse[]>
}

export const useTicketsStore = create<TicketsState>((set) => ({
  tickets: [],

  async fetchMine() {
    const tickets = await ticketsApi.mine()
    set({ tickets })
    return tickets
  },
}))
