/// <reference lib="webworker" />
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { CacheFirst, NetworkFirst } from 'workbox-strategies';
import { ExpirationPlugin } from 'workbox-expiration';
import { CacheableResponsePlugin } from 'workbox-cacheable-response';

declare const self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

// Admin routes opt out of the SPA offline fallback entirely — falling back to a cached
// shell while an admin is mid-edit would be worse than a hard error. Mirrors the old
// generateSW `navigateFallbackDenylist` config.
registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: [/^\/admin/] }));

// Product photos and other uploads change rarely once published, and are the single
// biggest win for repeat-visit load time.
registerRoute(
  ({ url }) => url.pathname.startsWith('/uploads/'),
  new CacheFirst({
    cacheName: 'uploads',
    plugins: [new ExpirationPlugin({ maxEntries: 300, maxAgeSeconds: 30 * 24 * 60 * 60 })],
  }),
);

// Public catalog/content reads: fresh data wins when online, cache covers flaky
// connections and the brief instant right after opening the app.
registerRoute(
  ({ url }) => url.pathname.startsWith('/api/public/'),
  new NetworkFirst({
    cacheName: 'public-api',
    networkTimeoutSeconds: 4,
    plugins: [
      new ExpirationPlugin({ maxEntries: 200, maxAgeSeconds: 5 * 60 }),
      new CacheableResponsePlugin({ statuses: [0, 200] }),
    ],
  }),
);

// vite-plugin-pwa's autoUpdate registration posts this once a new worker is waiting.
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

interface PushPayload {
  title?: string;
  body?: string;
  url?: string;
}

// Order-status updates (shipped / delivered / cancelled / refunded) — see
// PushNotificationService on the backend for the sender side.
self.addEventListener('push', (event) => {
  let payload: PushPayload = {};
  if (event.data) {
    try {
      payload = event.data.json() as PushPayload;
    } catch {
      payload = { body: event.data.text() };
    }
  }

  const title = payload.title ?? 'Bildiriş';
  const url = payload.url ?? '/';

  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body ?? '',
      icon: '/pwa-192.png',
      badge: '/pwa-192.png',
      data: { url },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data as { url?: string } | undefined)?.url ?? '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client && new URL(client.url).pathname === new URL(url, self.location.origin).pathname) {
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
