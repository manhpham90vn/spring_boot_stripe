import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  // Phục vụ dưới /admin/ qua nginx (web SPA chiếm root /).
  base: '/admin/',
  plugins: [vue()],
})
