import { Link } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import { Scale, X } from 'lucide-react';
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

  let lastGroup = '';

  return (
    <div className="container-site py-8 lg:py-12">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">Məhsulları müqayisə et</h1>

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
            ) : (
              rows.map((row) => {
                const showGroupHeader = row.groupName !== lastGroup;
                lastGroup = row.groupName;
                return (
                  <FragmentRow
                    key={row.attributeCode}
                    row={row}
                    showGroupHeader={showGroupHeader}
                    products={products}
                    valueFor={valueFor}
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
}: {
  row: { groupName: string; attributeCode: string; label: string };
  showGroupHeader: boolean;
  products: ProductDetail[];
  valueFor: (product: ProductDetail, attributeCode: string) => string | null;
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
      <tr className="border-b border-[var(--color-border)]">
        <td className="p-3 text-sm font-medium text-[var(--color-body)]">{row.label}</td>
        {products.map((product) => (
          <td key={product.id} className="p-3 text-center text-sm text-[var(--color-heading)]">
            {valueFor(product, row.attributeCode) ?? <span className="text-[var(--color-subtle)]">—</span>}
          </td>
        ))}
      </tr>
    </>
  );
}
