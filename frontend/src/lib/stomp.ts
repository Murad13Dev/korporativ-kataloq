import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tokenStore } from './api';

/**
 * One shared STOMP connection for the whole tab.
 *
 * Opening a socket per component would mean the storefront widget and the admin inbox
 * each hold their own, and a page with both would reconnect twice on every network blip.
 * Subscribers are reference-counted: the socket opens on the first subscription and
 * closes when the last one goes away.
 */

const WS_URL = `${window.location.origin}/ws`;

type MessageHandler = (payload: unknown) => void;

let client: Client | null = null;
let connected = false;

/** Destination → handlers. Kept so subscriptions can be replayed after a reconnect. */
const handlers = new Map<string, Set<MessageHandler>>();
const subscriptions = new Map<string, StompSubscription>();
const connectionListeners = new Set<(connected: boolean) => void>();

function ensureClient(): Client {
  if (client) {
    return client;
  }

  client = new Client({
    // SockJS rather than a raw WebSocket: corporate proxies frequently block the
    // upgrade, and the fallback keeps support reachable for exactly the customers
    // most likely to need it.
    webSocketFactory: () => new SockJS(WS_URL) as WebSocket,

    // The admin token is attached when present; visitors connect anonymously and are
    // authorised by their conversation token instead.
    connectHeaders: buildHeaders(),

    // Backoff is deliberately mild — a support chat that takes 30s to reconnect has
    // already lost the conversation.
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    onConnect: () => {
      connected = true;
      // Re-establish every subscription; the broker keeps none across a reconnect.
      handlers.forEach((_, destination) => openSubscription(destination));
      connectionListeners.forEach((listener) => listener(true));
    },

    onDisconnect: () => {
      connected = false;
      subscriptions.clear();
      connectionListeners.forEach((listener) => listener(false));
    },

    onWebSocketClose: () => {
      connected = false;
      subscriptions.clear();
      connectionListeners.forEach((listener) => listener(false));
    },

    onStompError: (frame) => {
      // Broker-level errors are usually an access denial on a guarded topic.
      console.error('STOMP error:', frame.headers['message'], frame.body);
    },
  });

  return client;
}

function buildHeaders(): Record<string, string> {
  const adminToken = tokenStore.getAdmin();
  return adminToken ? { Authorization: `Bearer ${adminToken}` } : {};
}

function openSubscription(destination: string): void {
  if (!client || !connected || subscriptions.has(destination)) {
    return;
  }

  const subscription = client.subscribe(destination, (frame: IMessage) => {
    let payload: unknown = frame.body;
    try {
      payload = JSON.parse(frame.body);
    } catch {
      // Non-JSON frames are passed through untouched rather than dropped.
    }
    handlers.get(destination)?.forEach((handler) => handler(payload));
  });

  subscriptions.set(destination, subscription);
}

/**
 * Subscribes to a destination.
 *
 * @returns an unsubscribe function; the socket closes once nothing is left subscribed
 */
export function subscribe(destination: string, handler: MessageHandler): () => void {
  const stomp = ensureClient();

  if (!handlers.has(destination)) {
    handlers.set(destination, new Set());
  }
  handlers.get(destination)!.add(handler);

  if (!stomp.active) {
    // Headers are rebuilt on activate so a token acquired after page load is used.
    stomp.connectHeaders = buildHeaders();
    stomp.activate();
  } else if (connected) {
    openSubscription(destination);
  }

  return () => {
    const set = handlers.get(destination);
    set?.delete(handler);

    if (set && set.size === 0) {
      handlers.delete(destination);
      subscriptions.get(destination)?.unsubscribe();
      subscriptions.delete(destination);
    }

    if (handlers.size === 0 && client) {
      void client.deactivate();
      connected = false;
    }
  };
}

/** Observes connection state, so the UI can show "bağlantı kəsildi" honestly. */
export function onConnectionChange(listener: (connected: boolean) => void): () => void {
  connectionListeners.add(listener);
  listener(connected);
  return () => connectionListeners.delete(listener);
}

export function isConnected(): boolean {
  return connected;
}

/** Forces a reconnect — used after an admin signs in so the token is sent. */
export function reconnect(): void {
  if (!client) {
    return;
  }
  client.connectHeaders = buildHeaders();
  if (client.active) {
    void client.deactivate().then(() => client?.activate());
  }
}

// ---------------------------------------------------------------------------
// Destinations — mirrors WebSocketConfig on the server.
// ---------------------------------------------------------------------------

export const topics = {
  /** The visitor's own thread; the token is the capability. */
  conversation: (visitorToken: string) => `/topic/chat/${visitorToken}`,
  adminInbox: '/topic/admin/chat',
};
