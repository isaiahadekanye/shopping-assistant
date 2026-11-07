/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // The Spring Boot backend; avoids CORS in development.
    proxy: {
      '/api': {
        target: process.env.API_URL ?? 'http://localhost:8080',
        // Browsers send every localhost cookie (from any app on any port). The API doesn't use cookies,
        // and forwarding them can exceed Tomcat's 8 KB header limit ("Request header is too large").
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('cookie')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    css: false,
  },
});
