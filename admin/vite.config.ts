import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Admin là web riêng, có domain riêng → serve ở root (base mặc định '/').
// Dev proxy: /api → API Gateway (cửa vào nghiệp vụ duy nhất).
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
    },
  },
})
