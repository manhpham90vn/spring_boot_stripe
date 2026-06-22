import { createRouter, createWebHistory } from 'vue-router'
import { authStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import VenuesView from '../views/VenuesView.vue'
import EventsView from '../views/EventsView.vue'
import EventDetailView from '../views/EventDetailView.vue'
import WaitingRoomView from '../views/WaitingRoomView.vue'

const router = createRouter({
  // BASE_URL theo vite.config (mặc định '/' vì admin chạy trên domain riêng).
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: '/events' },
    { path: '/events', name: 'events', component: EventsView },
    { path: '/events/:id', name: 'event-detail', component: EventDetailView, props: true },
    { path: '/venues', name: 'venues', component: VenuesView },
    { path: '/waiting-room', name: 'waiting-room', component: WaitingRoomView },
    { path: '/:pathMatch(.*)*', redirect: '/events' },
  ],
})

// Guard: bảo đảm đã khôi phục phiên, chặn route cần auth.
router.beforeEach(async (to) => {
  await authStore.init()
  if (!to.meta.public && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authStore.isAuthenticated) {
    return { name: 'events' }
  }
})

export default router
