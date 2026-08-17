import { useEffect, useState } from 'react';
import { postData } from '@/lib/api';
import { subscribe, topics } from '@/lib/stomp';

const HEARTBEAT_INTERVAL_MS = 20_000;

/**
 * Live "N people viewing this product" count.
 *
 * The first heartbeat's response gives an immediate number so the badge doesn't wait
 * for a STOMP round trip to appear; every count after that (including this visitor's
 * own repeat heartbeats) arrives over the `/topic/product/presence/{id}` broadcast, so
 * two tabs open on the same product agree without either one polling the other.
 */
export function useProductPresence(productId: number | undefined): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!productId) {
      return;
    }

    let cancelled = false;

    const heartbeat = () => {
      postData<number>(`/public/products/${productId}/presence`, undefined)
        .then((current) => {
          if (!cancelled) {
            setCount(current);
          }
        })
        .catch(() => {
          // A missed heartbeat just means a stale badge for one more interval.
        });
    };

    heartbeat();
    const interval = window.setInterval(heartbeat, HEARTBEAT_INTERVAL_MS);
    const unsubscribe = subscribe(topics.productPresence(productId), (payload) => {
      const next = (payload as { count?: number }).count;
      if (typeof next === 'number') {
        setCount(next);
      }
    });

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      unsubscribe();
    };
  }, [productId]);

  return count;
}
