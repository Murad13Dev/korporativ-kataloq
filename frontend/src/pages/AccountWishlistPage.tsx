import { Link } from 'react-router-dom';
import { Heart, Timer } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { ProductCard } from '@/components/catalog/ProductCard';
import { CountdownTimer } from '@/components/catalog/CountdownTimer';
import { useWishlist } from '@/hooks/useWishlist';

/** A wishlist item's campaign is treated as "ending soon" inside this window — long
    enough to be useful (a shopper checking once a day still catches it), short enough
    that the banner stays meaningful rather than permanently showing half the list. */
const ENDING_SOON_HOURS = 12;

export default function AccountWishlistPage() {
  const { data: products, isLoading } = useWishlist();

  if (isLoading) {
    return <SectionLoader label="Sevimlilər yüklənir…" />;
  }

  if (!products || products.length === 0) {
    return (
      <div className="card flex flex-col items-center gap-2 p-12 text-center text-[var(--color-subtle)]">
        <Heart size={28} aria-hidden="true" />
        <p className="text-sm">Sevimlilər siyahınız hələ boşdur</p>
        <Link to="/kataloq" className="mt-2 text-sm font-medium text-[var(--color-primary)] hover:underline">
          Kataloqa keç
        </Link>
      </div>
    );
  }

  const cutoff = Date.now() + ENDING_SOON_HOURS * 60 * 60 * 1000;
  const endingSoon = products.filter(
    (product) =>
      product.price.discounted &&
      product.price.showCountdown &&
      product.price.endsAt &&
      new Date(product.price.endsAt).getTime() <= cutoff,
  );

  return (
    <div className="space-y-5">
      {endingSoon.length > 0 && (
        <div className="rounded-[var(--radius-card)] border border-[var(--color-sale)]/30 bg-[var(--color-sale)]/5 p-4">
          <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-[var(--color-sale)]">
            <Timer size={16} aria-hidden="true" />
            Sevimlilərinizdən {endingSoon.length} kampaniya tezliklə bitir
          </p>
          <ul className="space-y-2.5">
            {endingSoon.map((product) => (
              <li key={product.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <Link
                  to={`/mehsul/${product.slug}`}
                  className="line-clamp-1 font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                >
                  {product.name}
                </Link>
                <CountdownTimer endsAt={product.price.endsAt!} variant="compact" />
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {products.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </div>
  );
}
