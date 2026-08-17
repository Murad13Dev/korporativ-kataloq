import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, deleteRequest, getData, postData, putData } from '@/lib/api';
import { useToast } from '@/components/ui/Toast';
import type { BulkAddResponse, Cart, CartNotice } from '@/types/api';

const CART_KEY = ['cart'] as const;

/**
 * The cart.
 *
 * Every mutation returns the *whole* re-priced cart, and the response replaces the cache
 * directly rather than triggering a refetch. That matters here: the server re-resolves
 * campaign prices on every read, so a second round trip could legitimately return
 * different numbers and make the UI flicker between two correct states.
 */
export function useCart(enabled = true) {
  return useQuery({
    queryKey: CART_KEY,
    queryFn: () => getData<Cart>('/public/cart'),
    enabled,
    staleTime: 30 * 1000,
    retry: 1,
  });
}

/** Lightweight count for the header badge. */
export function useCartCount(enabled = true) {
  const { data } = useCart(enabled);
  return data?.totals.totalQuantity ?? 0;
}

function useCartMutation<TVariables>(
  mutationFn: (variables: TVariables) => Promise<Cart>,
  options: { successMessage?: string; showNotices?: boolean } = {},
) {
  const queryClient = useQueryClient();
  const toast = useToast();

  return useMutation({
    mutationFn,
    onSuccess: (cart) => {
      queryClient.setQueryData(CART_KEY, cart);

      if (options.successMessage) {
        toast.success(options.successMessage);
      }

      // Notices are the server telling the shopper something changed underneath them —
      // a campaign started, stock ran short. Surfacing them here means the cart page and
      // the product grid both report it without duplicating the logic.
      if (options.showNotices !== false) {
        cart.notices.forEach((notice) => showNotice(toast, notice));
      }
    },
    onError: (error: ApiError) => {
      toast.error(error.message);
    },
  });
}

function showNotice(toast: ReturnType<typeof useToast>, notice: CartNotice) {
  const prefix = notice.productName ? `${notice.productName}: ` : '';
  switch (notice.type) {
    case 'PRICE_DROPPED':
      toast.success(`${prefix}${notice.message}`);
      break;
    case 'PRODUCT_UNAVAILABLE':
    case 'OUT_OF_STOCK':
      toast.error(`${prefix}${notice.message}`);
      break;
    default:
      toast.warning(`${prefix}${notice.message}`);
  }
}

export function useAddToCart() {
  return useCartMutation<{ productId: number; quantity?: number; variantId?: number }>(
    (variables) => postData<Cart>('/public/cart/items', variables),
    { successMessage: undefined },
  );
}

export function useUpdateCartItem() {
  return useCartMutation<{ itemId: number; quantity: number }>(({ itemId, quantity }) =>
    putData<Cart>(`/public/cart/items/${itemId}`, { quantity }),
  );
}

export function useRemoveCartItem() {
  return useCartMutation<number>(
    (itemId) => deleteRequest(`/public/cart/items/${itemId}`).then(() => getData<Cart>('/public/cart')),
    { successMessage: 'Məhsul səbətdən çıxarıldı' },
  );
}

export function useClearCart() {
  return useCartMutation<void>(
    () => deleteRequest('/public/cart').then(() => getData<Cart>('/public/cart')),
    { successMessage: 'Səbət boşaldıldı' },
  );
}

export function useApplyPromoCode() {
  return useCartMutation<string>(
    (code) => postData<Cart>('/public/cart/promo-code', { code }),
    { successMessage: 'Promo kod tətbiq edildi' },
  );
}

export function useRemovePromoCode() {
  return useCartMutation<void>(
    () => deleteRequest('/public/cart/promo-code').then(() => getData<Cart>('/public/cart')),
    { successMessage: 'Promo kod ləğv edildi' },
  );
}

/**
 * Shared by "reorder a past order" and "B2B bulk CSV import" — both return a cart plus a
 * report of what didn't make it in, rather than the plain `Cart` the single-item mutations
 * above return, so they can't reuse {@link useCartMutation} directly.
 */
function useBulkCartMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<BulkAddResponse>) {
  const queryClient = useQueryClient();
  const toast = useToast();

  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      queryClient.setQueryData(CART_KEY, result.cart);
      if (result.addedCount > 0) {
        toast.success(`${result.addedCount} məhsul səbətə əlavə edildi`);
      }
      // One toast per skip would flood the screen on a large CSV — a single combined
      // warning names the count and the first couple of reasons is enough to act on.
      if (result.skipped.length > 0) {
        const preview = result.skipped.slice(0, 2).join(' · ');
        toast.warning(
          `${result.skipped.length} sətir əlavə edilmədi: ${preview}${result.skipped.length > 2 ? '…' : ''}`,
        );
      }
    },
    onError: (error: ApiError) => toast.error(error.message),
  });
}

export function useReorder() {
  return useBulkCartMutation<number>((orderId) => postData<BulkAddResponse>(`/customer/orders/${orderId}/reorder`));
}

export function useBulkImport() {
  return useBulkCartMutation<{ sku: string; quantity: number }[]>((lines) =>
    postData<BulkAddResponse>('/public/cart/bulk-import', { lines }),
  );
}

export const cartQueryKey = CART_KEY;
