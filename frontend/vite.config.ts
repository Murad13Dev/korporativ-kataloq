// `defineConfig` comes from vitest/config rather than vite so the `test` block
// below is typed; it is a superset of Vite's own export.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // The admin panel must never run from a stale cache or serve cached data to
      // someone who thinks they're looking at the live catalog — only the public
      // storefront shell is precached, and only public GET endpoints get runtime
      // caching below.
      injectRegister: 'auto',
      manifest: {
        name: 'Korporativ Kataloq',
        short_name: 'Kataloq',
        description: 'Universal korporativ kataloq və e-ticarət platforması',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#0F172A',
        lang: 'az',
        icons: [
          { src: '/pwa-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Admin routes opt out of the SPA offline fallback entirely — falling back to
        // a cached shell while an admin is mid-edit would be worse than a hard error.
        navigateFallbackDenylist: [/^\/admin/],
        runtimeCaching: [
          {
            // Product photos and other uploads change rarely once published, and are
            // the single biggest win for repeat-visit load time.
            urlPattern: ({ url }) => url.pathname.startsWith('/uploads/'),
            handler: 'CacheFirst',
            options: {
              cacheName: 'uploads',
              expiration: { maxEntries: 300, maxAgeSeconds: 30 * 24 * 60 * 60 },
            },
          },
          {
            // Public catalog/content reads: fresh data wins when online, cache covers
            // flaky connections and the brief instant right after opening the app.
            urlPattern: ({ url }) => url.pathname.startsWith('/api/public/'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'public-api',
              networkTimeoutSeconds: 4,
              expiration: { maxEntries: 200, maxAgeSeconds: 5 * 60 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
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
