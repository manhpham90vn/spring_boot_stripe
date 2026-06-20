import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy: gọi thẳng API Gateway (cửa vào duy nhất cho traffic nghiệp vụ).
// Đổi đích qua biến môi trường API_TARGET khi gateway chạy ở host/cổng khác.
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
    },
  },
})
