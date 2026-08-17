import { useCallback, useMemo } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { PackageSearch, SlidersHorizontal, Tag } from 'lucide-react';
import { ProductCard, ProductCardSkeleton } from '@/components/catalog/ProductCard';
import { getPage, getData } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import type { CategoryCard, PageResponse, ProductCard as ProductCardType } from '@/types/api';

const SORT_OPTIONS = [
  { value: 'sortOrder,asc', label: 'Tövsiyə olunan' },
  { value: 'createdAt,desc', label: 'Ən yenilər' },
  { value: 'price,asc', label: 'Ucuzdan bahaya' },
  { value: 'price,desc', label: 'Bahadan ucuza' },
] as const;

const PAGE_SIZE = 24;

/**
 * Catalog listing.
 *
 * Every filter lives in the query string rather than in component state, so a filtered
 * view is a shareable URL and the back button behaves the way a shopper expects.
 */
export function CatalogPage() {
  const { categorySlug } = useParams<{ categorySlug?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const search = searchParams.get('search') ?? '';
  const sort = searchParams.get('sort') ?? 'sortOrder,asc';
  const page = Number(searchParams.get('page') ?? '0');
  const discountedOnly = searchParams.get('discounted') === 'true';
  const inStockOnly = searchParams.get('inStock') === 'true';

  const queryParams = useMemo(
    () => ({
      search: search || undefined,
      categorySlug: categorySlug || undefined,
      discounted: discountedOnly || undefined,
      inStock: inStockOnly || undefined,
      sort,
      page,
      size: PAGE_SIZE,
    }),
    [search, categorySlug, discountedOnly, inStockOnly, sort, page],
  );

  const productsQuery = useQuery({
    queryKey: ['products', queryParams],
    queryFn: () => getPage<ProductCardType>('/public/products', queryParams),
    placeholderData: (previous) => previous,
  });

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => getData<CategoryCard[]>('/public/categories'),
    staleTime: 10 * 60 * 1000,
  });

  /** Changing any filter resets to page 0 — staying on page 7 of a new result set is a dead end. */
  const updateFilter = useCallback(
    (key: string, value: string | null) => {
      const next = new URLSearchParams(searchParams);
      if (value === null || value === '') {
        next.delete(key);
      } else {
        next.set(key, value);
      }
      if (key !== 'page') {
        next.delete('page');
      }
      setSearchParams(next, { replace: key === 'page' ? false : true });
    },
    [searchParams, setSearchParams],
  );

  const products = productsQuery.data;
  const hasActiveFilters = discountedOnly || inStockOnly || Boolean(search);

  return (
    <div className="container-site py-8 lg:py-12">
      {/* ---------------- header ---------------- */}
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight lg:text-4xl">
          {discountedOnly ? 'Endirimdə olan məhsullar' : 'Kataloq'}
        </h1>
        {products && (
          <p className="mt-2 text-sm text-[var(--color-subtle)]">
            {formatNumber(products.totalElements)} məhsul tapıldı
          </p>
        )}
      </header>

      <div className="flex flex-col gap-8 lg:flex-row">
        {/* ---------------- filters ---------------- */}
        <aside className="lg:w-64 lg:shrink-0">
          <div className="sticky top-[calc(var(--header-height)+1rem)] space-y-6">
            <div className="card p-4">
              <label
                htmlFor="catalog-search"
                className="mb-2 block text-sm font-semibold text-[var(--color-heading)]"
              >
                Axtarış
              </label>
              <input
                id="catalog-search"
                type="search"
                defaultValue={search}
                placeholder="Məhsul və ya SKU"
                onChange={(event) => {
                  const value = event.target.value;
                  // Debounce so a search does not fire per keystroke.
                  window.clearTimeout((window as any).__catalogSearchTimer);
                  (window as any).__catalogSearchTimer = window.setTimeout(
                    () => updateFilter('search', value || null),
                    350,
                  );
                }}
                className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none transition-colors focus:border-[var(--color-primary)]"
              />
            </div>

            <div className="card p-4">
              <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--color-heading)]">
                <SlidersHorizontal size={15} aria-hidden="true" />
                Filtrlər
              </h2>

              <label className="flex cursor-pointer items-center gap-2.5 py-1.5 text-sm">
                <input
                  type="checkbox"
                  checked={discountedOnly}
                  onChange={(event) =>
                    updateFilter('discounted', event.target.checked ? 'true' : null)
                  }
                  className="h-4 w-4 rounded border-[var(--color-border)] accent-[var(--color-sale)]"
                />
                <span className="flex items-center gap-1.5">
                  <Tag size={14} className="text-[var(--color-sale)]" aria-hidden="true" />
                  Endirimdə
                </span>
              </label>

              <label className="flex cursor-pointer items-center gap-2.5 py-1.5 text-sm">
                <input
                  type="checkbox"
                  checked={inStockOnly}
                  onChange={(event) => updateFilter('inStock', event.target.checked ? 'true' : null)}
                  className="h-4 w-4 rounded border-[var(--color-border)] accent-[var(--color-primary)]"
                />
                Yalnız stokda olanlar
              </label>

              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={() => setSearchParams(new URLSearchParams())}
                  className="mt-3 text-sm font-medium text-[var(--color-primary)] hover:underline"
                >
                  Filtrləri təmizlə
                </button>
              )}
            </div>

            {categoriesQuery.data && categoriesQuery.data.length > 0 && (
              <nav className="card p-4" aria-label="Kateqoriyalar">
                <h2 className="mb-3 text-sm font-semibold text-[var(--color-heading)]">
                  Kateqoriyalar
                </h2>
                <ul className="space-y-1">
                  {categoriesQuery.data.map((category) => (
                    <li key={category.id}>
                      <a
                        href={`/kataloq/${category.slug}`}
                        className={clsx(
                          'flex items-center justify-between rounded-[var(--radius-button)] px-2 py-1.5 text-sm transition-colors',
                          categorySlug === category.slug
                            ? 'bg-[var(--color-muted)] font-medium text-[var(--color-primary)]'
                            : 'text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                        )}
                      >
                        <span>{category.name}</span>
                        <span className="text-xs text-[var(--color-subtle)] tabular">
                          {category.productCount}
                        </span>
                      </a>
                    </li>
                  ))}
                </ul>
              </nav>
            )}
          </div>
        </aside>

        {/* ---------------- results ---------------- */}
        <div className="min-w-0 flex-1">
          <div className="mb-5 flex items-center justify-between gap-4">
            <label htmlFor="catalog-sort" className="sr-only">
              Sıralama
            </label>
            <select
              id="catalog-sort"
              value={sort}
              onChange={(event) => updateFilter('sort', event.target.value)}
              className="ms-auto rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          {productsQuery.isLoading ? (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {Array.from({ length: 8 }).map((_, index) => (
                <ProductCardSkeleton key={index} />
              ))}
            </div>
          ) : products && products.content.length > 0 ? (
            <>
              <div
                // Remounts the grid (and re-triggers .stagger-fade) whenever the result
                // set actually changes — a filter tweak should feel like a fresh reveal,
                // not a silent content swap.
                key={JSON.stringify(queryParams)}
                className={clsx(
                  'stagger-fade grid grid-cols-2 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
                  // Keeps the grid readable while a filter change is in flight.
                  productsQuery.isFetching && 'opacity-60 transition-opacity',
                )}
              >
                {products.content.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>

              <Pagination page={products} onChange={(next) => updateFilter('page', String(next))} />
            </>
          ) : (
            <EmptyState hasFilters={hasActiveFilters} onClear={() => setSearchParams(new URLSearchParams())} />
          )}
        </div>
      </div>
    </div>
  );
}

function Pagination({
  page,
  onChange,
}: {
  page: PageResponse<unknown>;
  onChange: (page: number) => void;
}) {
  if (page.totalPages <= 1) {
    return null;
  }

  // Window of pages around the current one; the ends are always reachable.
  const pages = new Set<number>([0, page.totalPages - 1]);
  for (let offset = -2; offset <= 2; offset++) {
    const candidate = page.page + offset;
    if (candidate >= 0 && candidate < page.totalPages) {
      pages.add(candidate);
    }
  }
  const sorted = [...pages].sort((a, b) => a - b);

  return (
    <nav className="mt-10 flex items-center justify-center gap-1.5" aria-label="Səhifələr">
      <button
        type="button"
        disabled={page.first}
        onClick={() => onChange(page.page - 1)}
        className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-2 text-sm disabled:opacity-40"
      >
        Əvvəlki
      </button>

      {sorted.map((value, index) => (
        <span key={value} className="flex items-center gap-1.5">
          {index > 0 && sorted[index - 1]! < value - 1 && (
            <span className="px-1 text-[var(--color-subtle)]">…</span>
          )}
          <button
            type="button"
            onClick={() => onChange(value)}
            aria-current={value === page.page ? 'page' : undefined}
            className={clsx(
              'min-w-9 rounded-[var(--radius-button)] px-3 py-2 text-sm tabular transition-colors',
              value === page.page
                ? 'bg-[var(--color-primary)] font-semibold text-[var(--color-primary-contrast)]'
                : 'border border-[var(--color-border)] hover:bg-[var(--color-muted)]',
            )}
          >
            {value + 1}
          </button>
        </span>
      ))}

      <button
        type="button"
        disabled={page.last}
        onClick={() => onChange(page.page + 1)}
        className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-2 text-sm disabled:opacity-40"
      >
        Növbəti
      </button>
    </nav>
  );
}

function EmptyState({ hasFilters, onClear }: { hasFilters: boolean; onClear: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-[var(--color-border)] px-6 py-20 text-center">
      <PackageSearch size={40} className="animate-float mb-4 text-[var(--color-subtle)]" aria-hidden="true" />
      <h2 className="mb-2 text-lg font-semibold">Məhsul tapılmadı</h2>
      <p className="mb-6 max-w-sm text-sm text-[var(--color-body)]">
        {hasFilters
          ? 'Seçdiyiniz filtrlərə uyğun məhsul yoxdur. Filtrləri dəyişməyi sınayın.'
          : 'Bu bölmədə hələ məhsul yoxdur.'}
      </p>
      {hasFilters && (
        <button
          type="button"
          onClick={onClear}
          className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)]"
        >
          Filtrləri təmizlə
        </button>
      )}
    </div>
  );
}
