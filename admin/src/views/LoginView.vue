<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authStore } from '../stores/auth'
import { ApiError } from '../api/client'

const route = useRoute()
const router = useRouter()
const email = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)

async function submit() {
  error.value = null
  busy.value = true
  try {
    await authStore.login(email.value, password.value)
    if (authStore.state.user?.role !== 'ADMIN') {
      authStore.logout()
      error.value = 'Tài khoản này không có quyền ADMIN.'
      return
    }
    const redirect = (route.query.redirect as string) || '/events'
    router.push(redirect)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Đăng nhập thất bại.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div style="display: flex; align-items: center; justify-content: center; min-height: 100vh">
    <div class="panel" style="width: 380px">
      <h1>TicketHub Admin</h1>
      <p class="muted" style="margin-bottom: 20px">Đăng nhập bằng tài khoản quản trị.</p>

      <div v-if="error" class="alert">{{ error }}</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="email">Email</label>
          <input id="email" v-model="email" type="email" autocomplete="email" required />
        </div>
        <div class="field">
          <label for="password">Mật khẩu</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
          />
        </div>
        <button class="btn btn--primary" style="width: 100%" :disabled="busy">
          {{ busy ? 'Đang đăng nhập…' : 'Đăng nhập' }}
        </button>
      </form>
    </div>
  </div>
</template>
