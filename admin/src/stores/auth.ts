import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import { getToken, setToken } from '../api/client'
import type { UserResponse } from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserResponse | null>(null)
  const ready = ref(false) // đã thử khôi phục phiên xong chưa

  const isAuthenticated = computed(() => user.value !== null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  // Khôi phục phiên lúc khởi động nếu còn token (chạy đúng một lần).
  async function init() {
    if (ready.value) return
    if (getToken()) {
      try {
        user.value = await authApi.me()
      } catch {
        setToken(null)
      }
    }
    ready.value = true
  }

  async function login(email: string, password: string) {
    user.value = await authApi.login(email, password)
  }

  function logout() {
    setToken(null)
    user.value = null
  }

  return { user, ready, isAuthenticated, isAdmin, init, login, logout }
})
