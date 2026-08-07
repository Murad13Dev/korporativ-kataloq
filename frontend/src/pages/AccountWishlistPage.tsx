import { Link } from 'react-router-dom';
import { Heart } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { ProductCard } from '@/components/catalog/ProductCard';
import { useWishlist } from '@/hooks/useWishlist';

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

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}
