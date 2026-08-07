import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getData } from '@/lib/api';
import type { ProductCard } from '@/types/api';

const STORAGE_KEY = 'ctl.recentlyViewed';
const MAX_ENTRIES = 12;

function readIds(): number[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

/** Call once a product's detail page has actually loaded, to log the view. */
export function recordRecentlyViewed(productId: number): void {
  const existing = readIds().filter((id) => id !== productId);
  const next = [productId, ...existing].slice(0, MAX_ENTRIES);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

/**
 * The shopper's own browsing history, purely client-side — no account needed and
 * nothing about it is sent anywhere until this hook asks the catalog to resolve the
 * ids back into cards. `excludeId` drops the product currently on screen, since "you
 * were just looking at this" isn't a useful recommendation.
 */
export function useRecentlyViewed(excludeId?: number) {
  const [ids, setIds] = useState<number[]>([]);

  useEffect(() => {
    setIds(readIds());
  }, [excludeId]);

  const relevantIds = ids.filter((id) => id !== excludeId);

  const query = useQuery({
    queryKey: ['recently-viewed', relevantIds],
    // Comma-joined into one param rather than a repeated array param — Spring's
    // `List<Long>` binding accepts that directly, with no axios array-serialisation
    // format (`ids[]=` vs `ids=`) to get wrong.
    queryFn: () => getData<ProductCard[]>('/public/products/by-ids', { ids: relevantIds.join(',') }),
    enabled: relevantIds.length > 0,
  });

  return { products: query.data ?? [], isLoading: query.isLoading && relevantIds.length > 0 };
}
