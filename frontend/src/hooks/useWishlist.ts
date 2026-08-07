import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteRequest, getData, postVoid } from '@/lib/api';
import { useCustomerAuth } from './useCustomerAuth';
import type { ApiError } from '@/lib/api';
import type { ProductCard } from '@/types/api';

const WISHLIST_KEY = ['wishlist'] as const;

/**
 * Saved-for-later products. Only ever meaningful for a signed-in customer, so the query
 * stays disabled until `useCustomerAuth` has actually confirmed a session — it does not
 * force that check itself, matching the lazy-restore philosophy `AccountLayout` already
 * established (a catalog browser shouldn't pay for a session round trip just to render
 * outline hearts).
 */
export function useWishlist() {
  const status = useCustomerAuth((state) => state.status);

  return useQuery({
    queryKey: WISHLIST_KEY,
    queryFn: () => getData<ProductCard[]>('/customer/wishlist'),
    enabled: status === 'authenticated',
    staleTime: 30 * 1000,
    retry: false,
  });
}

/** Fast membership lookup for rendering filled vs. outline hearts across the catalog. */
export function useWishlistIds(): Set<number> {
  const { data } = useWishlist();
  return useMemo(() => new Set((data ?? []).map((product) => product.id)), [data]);
}

export function useToggleWishlist() {
  const queryClient = useQueryClient();

  const add = useMutation({
    mutationFn: (productId: number) => postVoid(`/customer/wishlist/${productId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WISHLIST_KEY }),
  });

  const remove = useMutation({
    mutationFn: (productId: number) => deleteRequest(`/customer/wishlist/${productId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WISHLIST_KEY }),
  });

  return {
    add: (productId: number) => add.mutate(productId),
    remove: (productId: number) => remove.mutate(productId),
    isPending: add.isPending || remove.isPending,
    error: (add.error ?? remove.error) as ApiError | null,
  };
}
