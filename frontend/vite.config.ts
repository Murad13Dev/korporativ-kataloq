// `defineConfig` comes from vitest/config rather than vite so the `test` block
// below is typed; it is a superset of Vite's own export.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Dev server talks to the Spring Boot app directly; in production nginx
      // does the same job, so application code only ever sees a relative /api.
      '/api': {
        target: process.env.BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: process.env.BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.BACKEND_URL ?? 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        // The admin panel is a large, rarely-visited bundle; splitting it keeps
        // the storefront's first paint small for actual shoppers.
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('react-router')) return 'router';
            if (id.includes('@tanstack')) return 'query';
            if (id.includes('stompjs') || id.includes('sockjs')) return 'realtime';
            if (id.includes('lucide-react')) return 'icons';
            return 'vendor';
          }
          if (id.includes('/src/admin/')) return 'admin';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
