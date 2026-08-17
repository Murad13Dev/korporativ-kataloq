import { useCallback, useEffect, useState } from 'react';
import { apiClient, getData, postVoid } from '@/lib/api';

export type PushSupportState = 'unsupported' | 'checking' | 'subscribed' | 'unsubscribed';

/**
 * Order-status push notifications (shipped / delivered / cancelled / refunded).
 *
 * The VAPID keypair is generated and persisted server-side on first boot (see
 * VapidKeyService), so the only thing the client needs is its public half to call
 * `pushManager.subscribe`. The resulting subscription is posted verbatim — its
 * `toJSON()` shape matches `PushDtos.SubscribeRequest` exactly — so there's no
 * reshaping on either side.
 */
export function usePushSubscription() {
  const [state, setState] = useState<PushSupportState>('checking');

  const refresh = useCallback(async () => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
      setState('unsupported');
      return;
    }
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      setState(subscription ? 'subscribed' : 'unsubscribed');
    } catch {
      setState('unsupported');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const subscribe = useCallback(async () => {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      return;
    }

    const publicKey = await getData<string>('/public/push/vapid-public-key');
    if (!publicKey) {
      return;
    }

    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });

    await postVoid('/customer/push/subscribe', subscription.toJSON());
    setState('subscribed');
  }, []);

  const unsubscribe = useCallback(async () => {
    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.getSubscription();
    if (!subscription) {
      setState('unsubscribed');
      return;
    }

    const endpoint = subscription.endpoint;
    await subscription.unsubscribe();
    await apiClient.delete('/customer/push/subscribe', { data: { endpoint } });
    setState('unsubscribed');
  }, []);

  return { state, subscribe, unsubscribe };
}

/** pushManager.subscribe requires the VAPID key as a raw Uint8Array, not the base64url string it's served as. */
function urlBase64ToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}
