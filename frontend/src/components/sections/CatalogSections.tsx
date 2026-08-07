import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { ProductCard, ProductCardSkeleton } from '@/components/catalog/ProductCard';
import { CountdownTimer } from '@/components/catalog/CountdownTimer';
import { ScrollRail } from '@/components/ui/ScrollRail';
import { isInverted, SectionButton, SectionShell } from './SectionShell';
import { getData, getPage } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import type { CategoryCard, ProductCard as ProductCardType } from '@/types/api';
import { configBoolean, configNumber, configString, type SectionProps } from './types';

/**
 * Sections that pull live catalog data.
 *
 * Each one hides itself entirely when it has nothing to show. A "Featured products"
 * heading above an empty grid looks broken; no section at all looks intentional — and on
 * a campaign section it is genuinely correct, because a campaign that ended should leave
 * no trace on the page.
 */

// ---------------------------------------------------------------------------
// FEATURED_CATEGORIES
// ---------------------------------------------------------------------------

export function FeaturedCategoriesSection({ section }: SectionProps) {
  const limit = configNumber(section, 'limit', 6, 24);
  const showCount = configBoolean(section, 'showProductCount', true);

  const query = useQuery({
    queryKey: ['sections', 'categories', 'featured', limit],
    queryFn: () => getData<CategoryCard[]>('/public/categories/featured', { limit }),
    staleTime: 10 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  return (
    <SectionShell section={section}>
      {query.isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          {Array.from({ length: limit }).map((_, index) => (
            <div key={index} className="skeleton h-32 rounded-[var(--radius-card)]" />
          ))}
        </div>
      ) : (
        <div
          className={clsx(
            section.variant === 'CAROUSEL'
              ? 'scroll-rail'
              : section.variant === 'COMPACT'
                ? 'flex flex-wrap gap-3'
                : // flex-wrap + justify-center rather than a fixed grid: a category count
                  // that isn't a multiple of the column count (4 categories on 6 columns,
                  // say) would otherwise strand the last row flush-start with visible
                  // empty tracks beside it instead of centering what's actually there.
                  'flex flex-wrap justify-center gap-4',
          )}
        >
          {query.data!.map((category) =>
            section.variant === 'COMPACT' ? (
              <Link
                key={category.id}
                to={`/kataloq/${category.slug}`}
                className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2 text-sm font-medium transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
              >
                {category.name}
                {showCount && (
                  <span className="ms-1.5 text-xs text-[var(--color-subtle)] tabular">
                    {category.productCount}
                  </span>
                )}
              </Link>
            ) : (
              <Link
                key={category.id}
                to={`/kataloq/${category.slug}`}
                className={clsx(
                  'card card-elevated group flex flex-col items-center gap-3 p-5 text-center',
                  section.variant === 'CAROUSEL'
                    ? 'w-40 shrink-0'
                    : 'w-[calc(50%-0.5rem)] sm:w-[calc(33.333%-0.667rem)] lg:w-[calc(16.667%-0.833rem)]',
                )}
              >
                <div className="flex h-14 w-14 items-center justify-center overflow-hidden rounded-full bg-[var(--color-muted)]">
                  {category.imageUrl ? (
                    <img
                      src={category.imageUrl}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <span className="text-lg font-bold text-[var(--color-primary)]">
                      {category.name.charAt(0)}
                    </span>
                  )}
                </div>
                <div>
                  <h3 className="text-sm font-semibold leading-tight transition-colors group-hover:text-[var(--color-primary)]">
                    {category.name}
                  </h3>
                  {showCount && (
                    <p className="mt-1 text-xs text-[var(--color-subtle)] tabular">
                      {formatNumber(category.productCount)} məhsul
                    </p>
                  )}
                </div>
              </Link>
            ),
          )}
        </div>
      )}

      {section.buttonText && (
        <div className="mt-8 text-center">
          <SectionButton text={section.buttonText} url={section.buttonUrl} variant="secondary" />
        </div>
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// FEATURED_PRODUCTS / DISCOUNTED_PRODUCTS / PRODUCT_LIST
// ---------------------------------------------------------------------------

export function FeaturedProductsSection({ section }: SectionProps) {
  const limit = configNumber(section, 'limit', 8, 24);

  const query = useQuery({
    queryKey: ['sections', 'products', 'featured', limit],
    queryFn: () => getData<ProductCardType[]>('/public/products/featured', { limit }),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <ProductRail
      section={section}
      products={query.data}
      isLoading={query.isLoading}
      limit={limit}
    />
  );
}

/**
 * Campaign rail.
 *
 * Renders nothing when no campaign is running — that is the correct behaviour, not an
 * empty state: a page should not advertise "Endirimlər" on a day there are none.
 */
export function DiscountedProductsSection({ section }: SectionProps) {
  const limit = configNumber(section, 'limit', 8, 24);
  const showCountdown = configBoolean(section, 'showCountdown', true);

  const query = useQuery({
    queryKey: ['sections', 'products', 'discounted', limit],
    queryFn: () => getData<ProductCardType[]>('/public/products/discounted', { limit }),
    // Campaigns start and end on a schedule, so this is refreshed more eagerly.
    staleTime: 2 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  // The soonest-ending campaign drives the section-level countdown.
  const nextEnding = (query.data ?? [])
    .map((product) => product.price.endsAt)
    .filter((value): value is string => Boolean(value))
    .sort()[0];

  return (
    <ProductRail
      section={section}
      products={query.data}
      isLoading={query.isLoading}
      limit={limit}
      headerExtra={
        showCountdown && nextEnding ? (
          <div className="mt-3">
            <CountdownTimer endsAt={nextEnding} variant="blocks" />
          </div>
        ) : null
      }
    />
  );
}

export function ProductListSection({ section }: SectionProps) {
  const limit = configNumber(section, 'limit', 8, 24);
  const categorySlug = configString(section, 'categorySlug');
  const categoryId = configNumber(section, 'categoryId', 0, 10_000_000);

  const query = useQuery({
    queryKey: ['sections', 'products', 'list', { limit, categorySlug, categoryId }],
    queryFn: () =>
      getPage<ProductCardType>('/public/products', {
        categorySlug: categorySlug ?? undefined,
        categoryId: categoryId > 0 ? categoryId : undefined,
        size: limit,
        page: 0,
      }).then((page) => page.content),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <ProductRail
      section={section}
      products={query.data}
      isLoading={query.isLoading}
      limit={limit}
    />
  );
}

/** Shared grid/carousel body for every product-backed section. */
function ProductRail({
  section,
  products,
  isLoading,
  limit,
  headerExtra,
}: {
  section: SectionProps['section'];
  products: ProductCardType[] | undefined;
  isLoading: boolean;
  limit: number;
  headerExtra?: React.ReactNode;
}) {
  const inverted = isInverted(section.backgroundStyle);

  if (!isLoading && (!products || products.length === 0)) {
    return null;
  }

  const isCarousel = section.variant === 'CAROUSEL';

  return (
    <SectionShell section={section} showHeader={false}>
      {(section.eyebrow || section.title || section.subtitle) && (
        <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div>
            {section.eyebrow && (
              <span
                className={clsx(
                  'text-sm font-semibold uppercase tracking-wide',
                  inverted ? 'text-white/70' : 'text-[var(--color-primary)]',
                )}
              >
                {section.eyebrow}
              </span>
            )}
            {section.title && (
              <h2
                id={`section-${section.id}-title`}
                className={clsx(
                  'mt-1 text-2xl font-bold tracking-tight lg:text-3xl',
                  inverted && 'text-white',
                )}
              >
                {section.title}
              </h2>
            )}
            {section.subtitle && (
              <p
                className={clsx(
                  'mt-1.5 text-sm',
                  inverted ? 'text-white/80' : 'text-[var(--color-body)]',
                )}
              >
                {section.subtitle}
              </p>
            )}
            {headerExtra}
          </div>

          <SectionButton
            text={section.buttonText}
            url={section.buttonUrl}
            variant="secondary"
            inverted={inverted}
          />
        </header>
      )}

      {(() => {
        const items = isLoading
          ? Array.from({ length: Math.min(limit, 4) }).map((_, index) => (
              <div key={index} className={clsx(!isCarousel && 'w-[calc(50%-0.5rem)] lg:w-[calc(25%-0.75rem)]')}>
                <ProductCardSkeleton />
              </div>
            ))
          : products!.map((product) => (
              <div
                key={product.id}
                className={clsx(
                  isCarousel
                    ? 'w-64 shrink-0'
                    : 'w-[calc(50%-0.5rem)] lg:w-[calc(25%-0.75rem)]',
                )}
              >
                <ProductCard product={product} />
              </div>
            ));

        return isCarousel ? (
          <ScrollRail inverted={inverted}>{items}</ScrollRail>
        ) : (
          // A plain grid leaves the last row's empty tracks visibly blank whenever the
          // item count isn't a multiple of the column count (5 featured products on a
          // 4-column grid, for instance) — flex-wrap + justify-center collapses that gap
          // by centering whatever lands in the trailing partial row instead of stranding
          // it flush-start with three empty slots beside it.
          <div className="flex flex-wrap justify-center gap-4">{items}</div>
        );
      })()}
    </SectionShell>
  );
}
