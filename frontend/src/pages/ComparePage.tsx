import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import clsx from 'clsx';
import { Scale, SplitSquareHorizontal, X } from 'lucide-react';
import { PriceDisplay } from '@/components/catalog/PriceDisplay';
import { PageLoader } from '@/components/ui/PageLoader';
import { useCompareStore } from '@/hooks/useCompare';
import { getData } from '@/lib/api';
import type { ProductDetail } from '@/types/api';

/**
 * Side-by-side spec comparison for the shortlist built from product cards.
 *
 * Fetches full `ProductDetail` per slug — the only endpoint carrying specifications —
 * rather than the lighter `by-ids` card endpoint the other shortlists (wishlist,
 * recently viewed) use. At most 4 items, so 4 parallel requests is the honest cost of
 * a real comparison table, not the padded stats a `ProductCard` already has.
 */
export default function ComparePage() {
  const slugs = useCompareStore((state) => state.slugs);
  const remove = useCompareStore((state) => state.remove);
  const [onlyDifferences, setOnlyDifferences] = useState(false);

  const queries = useQueries({
    queries: slugs.map((slug) => ({
      queryKey: ['product', slug],
      queryFn: () => getData<ProductDetail>(`/public/products/${slug}`),
    })),
  });

  const loading = queries.some((query) => query.isLoading);
  const products = queries.map((query) => query.data).filter((product): product is ProductDetail => Boolean(product));

  if (slugs.length === 0) {
    return (
      <div className="container-site flex min-h-[50vh] flex-col items-center justify-center gap-3 py-16 text-center">
        <Scale size={32} className="text-[var(--color-subtle)]" aria-hidden="true" />
        <h1 className="text-xl font-bold">Müqayisə siyahınız boşdur</h1>
        <p className="max-w-sm text-sm text-[var(--color-subtle)]">
          Kataloqda məhsul kartlarındakı tərəzi işarəsinə klikləyərək müqayisəyə əlavə edin.
        </p>
        <Link to="/kataloq" className="mt-2 text-sm font-medium text-[var(--color-primary)] hover:underline">
          Kataloqa keç
        </Link>
      </div>
    );
  }

  if (loading) {
    return <PageLoader />;
  }

  // Union of every spec line across the fetched products, in first-seen order, so a
  // row exists even when only one product actually has that attribute.
  const rows: { groupName: string; attributeCode: string; label: string; unit: string | null }[] = [];
  const seen = new Set<string>();
  for (const product of products) {
    for (const group of product.specifications) {
      for (const line of group.lines) {
        const key = `${group.groupCode}:${line.attributeCode}`;
        if (!seen.has(key)) {
          seen.add(key);
          rows.push({
            groupName: group.groupName,
            attributeCode: line.attributeCode,
            label: line.label,
            unit: line.unit,
          });
        }
      }
    }
  }

  const valueFor = (product: ProductDetail, attributeCode: string): string | null => {
    for (const group of product.specifications) {
      const line = group.lines.find((entry) => entry.attributeCode === attributeCode);
      if (line) {
        return line.unit ? `${line.value} ${line.unit}` : line.value;
      }
    }
    return null;
  };

  // A row "differs" when the products don't all report the same value for it — missing
  // vs. present counts as a difference too, since that's exactly the kind of thing a
  // shopper comparing products wants pointed out, not silently normalised away.
  const rowDiffers = (row: { attributeCode: string }): boolean => {
    if (products.length < 2) {
      return false;
    }
    const values = products.map((product) => valueFor(product, row.attributeCode) ?? '—');
    return new Set(values).size > 1;
  };

  const visibleRows = onlyDifferences ? rows.filter(rowDiffers) : rows;

  let lastGroup = '';

  return (
    <div className="container-site py-8 lg:py-12">
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold tracking-tight">Məhsulları müqayisə et</h1>

        {products.length > 1 && rows.length > 0 && (
          <button
            type="button"
            onClick={() => setOnlyDifferences((current) => !current)}
            aria-pressed={onlyDifferences}
            className={clsx(
              'flex items-center gap-2 rounded-[var(--radius-button)] border px-4 py-2 text-sm font-medium transition-colors',
              onlyDifferences
                ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                : 'border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
            )}
          >
            <SplitSquareHorizontal size={15} aria-hidden="true" />
            Yalnız fərqləri göstər
          </button>
        )}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] border-collapse">
          <thead>
            <tr>
              <th className="w-48 p-3 text-start text-sm font-semibold text-[var(--color-subtle)]" />
              {products.map((product) => (
                <th key={product.id} className="min-w-[200px] p-3 align-top">
                  <div className="relative rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
                    <button
                      type="button"
                      onClick={() => remove(product.slug)}
                      aria-label={`${product.name} müqayisədən çıxar`}
                      className="absolute end-2 top-2 rounded p-1 text-[var(--color-subtle)] hover:bg-[var(--color-muted)] hover:text-[var(--color-danger)]"
                    >
                      <X size={15} aria-hidden="true" />
                    </button>
                    <div className="aspect-square overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
                      {product.images[0] && (
                        <img
                          src={product.images[0].url}
                          alt=""
                          className="h-full w-full object-cover"
                        />
                      )}
                    </div>
                    <Link
                      to={`/mehsul/${product.slug}`}
                      className="mt-3 block line-clamp-2 text-sm font-semibold text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                    >
                      {product.name}
                    </Link>
                    <div className="mt-2">
                      <PriceDisplay price={product.price} showPrice={product.showPrice} size="sm" />
                    </div>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={products.length + 1} className="p-6 text-center text-sm text-[var(--color-subtle)]">
                  Seçilmiş məhsullarda müqayisə ediləcək xüsusiyyət yoxdur
                </td>
              </tr>
            ) : visibleRows.length === 0 ? (
              <tr>
                <td colSpan={products.length + 1} className="p-6 text-center text-sm text-[var(--color-subtle)]">
                  Seçilmiş məhsullar arasında fərq yoxdur
                </td>
              </tr>
            ) : (
              visibleRows.map((row) => {
                const showGroupHeader = !onlyDifferences && row.groupName !== lastGroup;
                lastGroup = row.groupName;
                return (
                  <FragmentRow
                    key={row.attributeCode}
                    row={row}
                    showGroupHeader={showGroupHeader}
                    products={products}
                    valueFor={valueFor}
                    differs={rowDiffers(row)}
                  />
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FragmentRow({
  row,
  showGroupHeader,
  products,
  valueFor,
  differs,
}: {
  row: { groupName: string; attributeCode: string; label: string };
  showGroupHeader: boolean;
  products: ProductDetail[];
  valueFor: (product: ProductDetail, attributeCode: string) => string | null;
  differs: boolean;
}) {
  return (
    <>
      {showGroupHeader && (
        <tr>
          <td
            colSpan={products.length + 1}
            className="bg-[var(--color-muted)] px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]"
          >
            {row.groupName}
          </td>
        </tr>
      )}
      <tr className={clsx('border-b border-[var(--color-border)]', differs && 'bg-[var(--color-primary)]/[0.04]')}>
        <td className="p-3 text-sm font-medium text-[var(--color-body)]">
          <span className="flex items-center gap-1.5">
            {row.label}
            {differs && (
              <span
                className="h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]"
                title="Məhsullar arasında fərqlənir"
                aria-hidden="true"
              />
            )}
          </span>
        </td>
        {products.map((product) => (
          <td
            key={product.id}
            className={clsx(
              'p-3 text-center text-sm',
              differs ? 'font-semibold text-[var(--color-primary)]' : 'text-[var(--color-heading)]',
            )}
          >
            {valueFor(product, row.attributeCode) ?? <span className="font-normal text-[var(--color-subtle)]">—</span>}
          </td>
        ))}
      </tr>
    </>
  );
}
