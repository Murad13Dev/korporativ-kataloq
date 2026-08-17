import { memo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { AnimatePresence, motion } from 'framer-motion';
import { Check, Heart, ImageOff, Loader2, Scale, ShoppingCart } from 'lucide-react';
import { DiscountRibbon, StatusBadges } from './DiscountRibbon';
import { PriceDisplay } from './PriceDisplay';
import { CountdownTimer } from './CountdownTimer';
import { useAddToCart } from '@/hooks/useCart';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { useCustomerAuth } from '@/hooks/useCustomerAuth';
import { useToggleWishlist, useWishlistIds } from '@/hooks/useWishlist';
import { MAX_COMPARE_ITEMS, useCompareStore } from '@/hooks/useCompare';
import { useToast } from '@/components/ui/Toast';
import type { ProductCard as ProductCardType } from '@/types/api';

/**
 * Product grid card.
 *
 * Layout decisions worth stating:
 * - The image sits in a fixed 4:3 box so a grid of mixed-aspect photographs still
 *   produces aligned rows — ragged card heights are the fastest way to make a catalog
 *   look unfinished.
 * - The discount ribbon takes the start corner, status badges the end corner, so they
 *   can never collide.
 * - "Səbətə at" is an inline action with its own success state. Sending a shopper to a
 *   detail page just to add a known item is friction; the confirmation stays on the card.
 */
export const ProductCard = memo(function ProductCard({
  product,
  showAddToCart = true,
  className,
}: {
  product: ProductCardType;
  showAddToCart?: boolean;
  className?: string;
}) {
  const { features } = useSiteConfig();
  const addToCart = useAddToCart();
  const [justAdded, setJustAdded] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const authStatus = useCustomerAuth((state) => state.status);
  const restore = useCustomerAuth((state) => state.restore);
  const wishlistIds = useWishlistIds();
  const toggleWishlist = useToggleWishlist();
  const wishlisted = wishlistIds.has(product.id);
  const compareSlugs = useCompareStore((state) => state.slugs);
  const toggleCompare = useCompareStore((state) => state.toggle);
  const compared = compareSlugs.includes(product.slug);
  const toast = useToast();

  const outOfStock = product.stockStatus === 'OUT_OF_STOCK';
  const lowStock = product.stockStatus === 'LOW_STOCK';
  const canBuy =
    showAddToCart && features.cartEnabled && product.purchasable && !outOfStock && product.showPrice;

  const handleAdd = (event: React.MouseEvent) => {
    // The card is a link; the button inside it must not navigate.
    event.preventDefault();
    event.stopPropagation();

    addToCart.mutate(
      { productId: product.id, quantity: 1 },
      {
        onSuccess: () => {
          setJustAdded(true);
          window.setTimeout(() => setJustAdded(false), 2000);
        },
      },
    );
  };

  const handleToggleWishlist = async (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();

    // The very first click of a session doesn't yet know if there's a live session —
    // `restore()` settles that once, and every card after benefits from the same check.
    let status = authStatus;
    if (status === 'idle') {
      await restore();
      status = useCustomerAuth.getState().status;
    }

    if (status !== 'authenticated') {
      navigate(`/hesab/giris?next=${encodeURIComponent(location.pathname)}`);
      return;
    }

    if (wishlisted) {
      toggleWishlist.remove(product.id);
    } else {
      toggleWishlist.add(product.id);
    }
  };

  const handleToggleCompare = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();

    if (!compared && compareSlugs.length >= MAX_COMPARE_ITEMS) {
      toast.error(`Eyni anda maksimum ${MAX_COMPARE_ITEMS} məhsul müqayisə edilə bilər`);
      return;
    }
    toggleCompare(product.slug);
  };

  return (
    <article
      className={clsx(
        'card card-elevated group flex h-full flex-col',
        outOfStock && 'opacity-75',
        className,
      )}
    >
      <Link
        to={`/mehsul/${product.slug}`}
        className="flex flex-1 flex-col focus-visible:outline-none"
        aria-label={product.name}
      >
        {/* --- media ------------------------------------------------------- */}
        <div className="product-media relative aspect-[4/3] overflow-hidden bg-[var(--color-muted)]">
          <DiscountRibbon price={product.price} />
          <StatusBadges
            isNew={product.isNew}
            featured={product.featured}
            lowStock={lowStock}
            outOfStock={outOfStock}
          />

          <div className="absolute end-3 bottom-3 z-10 flex flex-col gap-1.5">
            <button
              type="button"
              onClick={(event) => void handleToggleCompare(event)}
              aria-label={compared ? 'Müqayisədən çıxar' : 'Müqayisəyə əlavə et'}
              aria-pressed={compared}
              className={clsx(
                'flex h-8 w-8 items-center justify-center rounded-full',
                'bg-white/85 shadow-sm backdrop-blur transition-transform duration-150 hover:scale-110',
                'dark:bg-black/50',
              )}
            >
              <Scale
                size={15}
                aria-hidden="true"
                className={compared ? 'text-[var(--color-primary)]' : 'text-[var(--color-subtle)]'}
              />
            </button>

            {features.wishlistEnabled && (
              <motion.button
                type="button"
                whileTap={{ scale: 0.8 }}
                onClick={(event) => void handleToggleWishlist(event)}
                aria-label={wishlisted ? 'Sevimlilərdən çıxar' : 'Sevimlilərə əlavə et'}
                aria-pressed={wishlisted}
                className={clsx(
                  'flex h-8 w-8 items-center justify-center rounded-full',
                  'bg-white/85 shadow-sm backdrop-blur transition-transform duration-150 hover:scale-110',
                  'dark:bg-black/50',
                )}
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={wishlisted ? 'filled' : 'empty'}
                    initial={{ scale: 0.5, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ duration: 0.22, ease: [0.34, 1.56, 0.64, 1] }}
                    className="flex"
                  >
                    <Heart
                      size={16}
                      aria-hidden="true"
                      className={clsx(
                        'transition-colors',
                        wishlisted ? 'fill-[var(--color-danger)] text-[var(--color-danger)]' : 'text-[var(--color-subtle)]',
                      )}
                    />
                  </motion.span>
                </AnimatePresence>
              </motion.button>
            )}
          </div>

          {product.imageUrl && !imageFailed ? (
            <img
              src={product.imageUrl}
              alt={product.imageAlt ?? product.name}
              loading="lazy"
              decoding="async"
              onError={() => setImageFailed(true)}
              className="h-full w-full object-cover transition-transform duration-500 ease-[var(--ease-out-quint)] group-hover:scale-105"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-[var(--color-subtle)]">
              <ImageOff size={32} strokeWidth={1.5} aria-hidden="true" />
            </div>
          )}
        </div>

        {/* --- body -------------------------------------------------------- */}
        <div className="flex flex-1 flex-col gap-2 p-4">
          {product.categoryName && (
            <span className="text-xs font-medium uppercase tracking-wide text-[var(--color-primary)]">
              {product.categoryName}
            </span>
          )}

          <h3 className="line-clamp-2 text-base font-semibold leading-snug text-[var(--color-heading)] transition-colors group-hover:text-[var(--color-primary)]">
            {product.name}
          </h3>

          {product.shortDescription && (
            <p className="line-clamp-2 text-sm text-[var(--color-body)]">
              {product.shortDescription}
            </p>
          )}

          {/* Pushes the price block to the bottom so cards of differing text
              length still line their prices up. */}
          <div className="mt-auto space-y-2 pt-2">
            <PriceDisplay price={product.price} showPrice={product.showPrice} size="md" />

            {product.price.showCountdown && product.price.endsAt && (
              <CountdownTimer endsAt={product.price.endsAt} variant="compact" />
            )}
          </div>
        </div>
      </Link>

      {/* --- action -------------------------------------------------------- */}
      {canBuy && (
        <div className="px-4 pb-4">
          <button
            type="button"
            onClick={handleAdd}
            disabled={addToCart.isPending}
            className={clsx(
              'flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] px-4 py-2.5',
              'text-sm font-semibold transition-all duration-200',
              'disabled:cursor-not-allowed disabled:opacity-70',
              justAdded
                ? 'bg-[var(--color-success)] text-white'
                : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]',
            )}
          >
            {addToCart.isPending ? (
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            ) : justAdded ? (
              <Check size={16} aria-hidden="true" />
            ) : (
              <ShoppingCart size={16} aria-hidden="true" />
            )}
            <span>{justAdded ? 'Səbətə əlavə edildi' : 'Səbətə at'}</span>
          </button>
        </div>
      )}

      {outOfStock && showAddToCart && (
        <div className="px-4 pb-4">
          <div className="rounded-[var(--radius-button)] border border-dashed border-[var(--color-border)] px-4 py-2.5 text-center text-sm text-[var(--color-subtle)]">
            Anbarda yoxdur
          </div>
        </div>
      )}
    </article>
  );
});

/** Matches the real card's proportions so the grid does not jump when data lands. */
export function ProductCardSkeleton() {
  return (
    <div className="card flex h-full flex-col">
      <div className="skeleton aspect-[4/3] rounded-none" />
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="skeleton h-3 w-20" />
        <div className="skeleton h-4 w-full" />
        <div className="skeleton h-4 w-2/3" />
        <div className="mt-auto skeleton h-6 w-24" />
      </div>
    </div>
  );
}
