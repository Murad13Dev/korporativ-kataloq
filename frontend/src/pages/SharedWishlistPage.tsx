import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Gift, PackageSearch } from 'lucide-react';
import { PageLoader } from '@/components/ui/PageLoader';
import { ProductCard } from '@/components/catalog/ProductCard';
import { getData } from '@/lib/api';
import type { ProductCard as ProductCardType } from '@/types/api';

/**
 * Someone else's wishlist, read-only. The token is the whole access check (see the
 * backend's own note on this) — an unknown or expired token renders the same empty
 * state as an intentionally-empty list, so it never confirms or denies whether a token
 * ever existed.
 */
export default function SharedWishlistPage() {
  const { token } = useParams<{ token: string }>();

  const query = useQuery({
    queryKey: ['wishlist', 'shared', token],
    queryFn: () => getData<ProductCardType[]>(`/public/wishlist/${token}`),
    enabled: Boolean(token),
  });

  if (query.isLoading) {
    return <PageLoader />;
  }

  const products = query.data ?? [];

  return (
    <div className="container-site py-8 lg:py-12">
      <header className="mb-8 flex items-center gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)]">
          <Gift size={20} aria-hidden="true" />
        </span>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Sevimlilər siyahısı</h1>
          <p className="text-sm text-[var(--color-subtle)]">Sizinlə paylaşılmış hədiyyə siyahısı</p>
        </div>
      </header>

      {products.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-[var(--color-border)] px-6 py-20 text-center">
          <PackageSearch size={40} className="animate-float mb-4 text-[var(--color-subtle)]" aria-hidden="true" />
          <h2 className="mb-2 text-lg font-semibold">Siyahı boşdur</h2>
          <p className="mb-6 max-w-sm text-sm text-[var(--color-body)]">
            Bu link etibarsızdır və ya siyahıda hələ məhsul yoxdur.
          </p>
          <Link
            to="/kataloq"
            className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)]"
          >
            Kataloqa bax
          </Link>
        </div>
      ) : (
        <div className="stagger-fade grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}
