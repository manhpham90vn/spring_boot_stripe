import { create } from 'zustand'
import { authApi } from '../api/auth'
import { getToken, setToken } from '../api/client'
import type { UserResponse } from '../types/auth'

interface AuthState {
  user: UserResponse | null
  loading: boolean // đang khôi phục phiên lúc khởi động
  init: () => Promise<void>
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

let initStarted = false

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  loading: true,

  // Khởi động: nếu còn token thì khôi phục danh tính bằng /api/auth/me (chạy đúng một lần).
  async init() {
    if (initStarted) return
    initStarted = true
    if (!getToken()) {
      set({ loading: false })
      return
    }
    try {
      set({ user: await authApi.me() })
    } catch {
      setToken(null)
    } finally {
      set({ loading: false })
    }
  },

  async login(email, password) {
    await authApi.login(email, password)
    set({ user: await authApi.me() })
  },

  async register(email, password) {
    await authApi.register(email, password)
    await get().login(email, password)
  },

  logout() {
    setToken(null)
    set({ user: null })
  },
}))
