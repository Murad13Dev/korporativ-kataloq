import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Loader2, Search, X } from 'lucide-react';
import { PriceDisplay } from '@/components/catalog/PriceDisplay';
import { getPage } from '@/lib/api';
import type { ProductCard as ProductCardType } from '@/types/api';

/**
 * Live search dropdown, opened from the header's search button.
 *
 * Reuses the catalog's own `/public/products?search=` endpoint at a small page size
 * rather than a dedicated suggestion endpoint — the ranking and stock/price rules a
 * shopper sees here must already match the full catalog exactly, so there is nothing a
 * second endpoint would get right that this one doesn't already.
 */
export function SearchOverlay({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const resultsQuery = useQuery({
    queryKey: ['search-suggest', debounced],
    queryFn: () =>
      getPage<ProductCardType>('/public/products', { search: debounced, size: 6, sort: 'sortOrder,asc' }),
    enabled: debounced.length >= 2,
  });

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed) {
      navigate(`/kataloq?search=${encodeURIComponent(trimmed)}`);
      onClose();
    }
  };

  const results = resultsQuery.data?.content ?? [];
  const showPanel = debounced.length >= 2;

  return (
    <div className="border-t border-[var(--color-border)] bg-[var(--color-canvas)]">
      <div className="container-site py-4">
        <form onSubmit={submit} className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search
              size={18}
              className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]"
              aria-hidden="true"
            />
            <input
              ref={inputRef}
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Nə axtarırsınız?"
              aria-label="Məhsul axtar"
              className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] py-2.5 ps-10 pe-3 text-sm outline-none transition-colors focus:border-[var(--color-primary)]"
            />
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Axtarışı bağla"
            className="shrink-0 rounded-[var(--radius-button)] p-2.5 text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)]"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </form>

        {showPanel && (
          <div className="mt-2">
            {resultsQuery.isFetching ? (
              <div className="flex items-center gap-2 py-4 text-sm text-[var(--color-subtle)]">
                <Loader2 size={16} className="animate-spin" aria-hidden="true" />
                Axtarılır…
              </div>
            ) : results.length > 0 ? (
              <ul className="divide-y divide-[var(--color-border)]">
                {results.map((product) => (
                  <li key={product.id}>
                    <Link
                      to={`/mehsul/${product.slug}`}
                      onClick={onClose}
                      className="flex items-center gap-3 py-2.5 transition-colors hover:bg-[var(--color-muted)]/50"
                    >
                      <div className="h-11 w-11 shrink-0 overflow-hidden rounded-[var(--radius-input)] bg-[var(--color-muted)]">
                        {product.imageUrl && (
                          <img src={product.imageUrl} alt="" className="h-full w-full object-cover" />
                        )}
                      </div>
                      <span className="min-w-0 flex-1 truncate text-sm font-medium text-[var(--color-heading)]">
                        {product.name}
                      </span>
                      <PriceDisplay
                        price={product.price}
                        showPrice={product.showPrice}
                        size="sm"
                        showSaving={false}
                        className="shrink-0"
                      />
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="py-4 text-sm text-[var(--color-subtle)]">Nəticə tapılmadı</p>
            )}

            {results.length > 0 && (
              <Link
                to={`/kataloq?search=${encodeURIComponent(debounced)}`}
                onClick={onClose}
                className="mt-1 block border-t border-[var(--color-border)] py-2.5 text-center text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                Bütün nəticələri göstər
              </Link>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
