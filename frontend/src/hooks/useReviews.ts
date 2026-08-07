import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getData, getPage, postData } from '@/lib/api';
import type { RatingSummary, Review } from '@/types/api';

export function useRatingSummary(slug: string | undefined) {
  return useQuery({
    queryKey: ['product', slug, 'reviews', 'summary'],
    queryFn: () => getData<RatingSummary>(`/public/products/${slug}/reviews/summary`),
    enabled: Boolean(slug),
  });
}

export function useReviews(slug: string | undefined, page: number) {
  return useQuery({
    queryKey: ['product', slug, 'reviews', page],
    queryFn: () => getPage<Review>(`/public/products/${slug}/reviews`, { page, size: 10 }),
    enabled: Boolean(slug),
  });
}

export function useSubmitReview(slug: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: { productId: number; rating: number; title?: string; body?: string }) =>
      postData<Review>('/customer/reviews', values),
    onSuccess: () => {
      // The new review is PENDING until an admin approves it, so the public list and
      // summary won't show it yet regardless — this just clears any stale cache.
      queryClient.invalidateQueries({ queryKey: ['product', slug, 'reviews'] });
    },
  });
}
