import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// base '/admin/' để serve dưới đường dẫn con sau gateway/ingress.
// Dev proxy: /api → API Gateway (cửa vào nghiệp vụ duy nhất).
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  server: {
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
    },
  },
})
