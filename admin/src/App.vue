<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showChrome = computed(() => route.name !== 'login' && auth.isAuthenticated)

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="showChrome" class="shell">
    <aside class="sidebar">
      <div class="sidebar__brand">Ticket<span>Hub</span> Admin</div>
      <RouterLink :to="{ name: 'events' }">🎫 Sự kiện</RouterLink>
      <RouterLink :to="{ name: 'venues' }">📍 Địa điểm</RouterLink>
      <RouterLink :to="{ name: 'waiting-room' }">⏳ Waiting Room</RouterLink>
      <div class="sidebar__foot">
        <div>{{ auth.user?.email }}</div>
        <div class="muted">{{ auth.user?.role }}</div>
        <button class="btn btn--sm" style="margin-top: 10px" @click="logout">Đăng xuất</button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
  <RouterView v-else />
</template>
