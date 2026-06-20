<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { authStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const showChrome = computed(() => route.name !== 'login' && authStore.isAuthenticated)

function logout() {
  authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="showChrome" class="shell">
    <aside class="sidebar">
      <div class="sidebar__brand">Ticket<span>Hub</span> Admin</div>
      <RouterLink :to="{ name: 'events' }">🎫 Sự kiện</RouterLink>
      <RouterLink :to="{ name: 'venues' }">📍 Địa điểm</RouterLink>
      <div class="sidebar__foot">
        <div>{{ authStore.state.user?.email }}</div>
        <div class="muted">{{ authStore.state.user?.role }}</div>
        <button class="btn btn--sm" style="margin-top: 10px" @click="logout">Đăng xuất</button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
  <RouterView v-else />
</template>
