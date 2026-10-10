import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Dev server proxies /api to the Spring Boot API, so the browser makes SAME-ORIGIN requests and
// CORS never enters the picture during development. In a production build, set VITE_API_URL to the
// deployed API origin instead (see .env.example).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
