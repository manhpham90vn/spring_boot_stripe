import { reactive, readonly } from 'vue'
import { auth as authApi } from '../api/endpoints'
import { getToken, setToken } from '../api/client'
import type { UserResponse } from '../api/types'

// Store đăng nhập đơn giản dùng reactive singleton (đủ cho admin, không cần Pinia).
interface AuthState {
  user: UserResponse | null
  ready: boolean // đã thử khôi phục phiên xong chưa
}

const state = reactive<AuthState>({ user: null, ready: false })

export const authStore = {
  state: readonly(state),

  // Khôi phục phiên lúc khởi động nếu còn token.
  async init() {
    if (state.ready) return
    if (getToken()) {
      try {
        state.user = await authApi.me()
      } catch {
        setToken(null)
      }
    }
    state.ready = true
  },

  async login(email: string, password: string) {
    state.user = await authApi.login(email, password)
  },

  logout() {
    setToken(null)
    state.user = null
  },

  get isAuthenticated() {
    return state.user !== null
  },
}
