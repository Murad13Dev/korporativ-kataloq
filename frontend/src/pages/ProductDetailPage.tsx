import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  Check,
  ChevronRight,
  Facebook,
  FileText,
  Heart,
  Link2,
  Loader2,
  MessageCircle,
  Minus,
  Plus,
  Send,
  ShoppingCart,
  Star,
} from 'lucide-react';
import { PriceDisplay } from '@/components/catalog/PriceDisplay';
import { CountdownTimer } from '@/components/catalog/CountdownTimer';
import { DiscountPill } from '@/components/catalog/DiscountRibbon';
import { ProductCard, ProductCardSkeleton } from '@/components/catalog/ProductCard';
import { useToast } from '@/components/ui/Toast';
import { Field, inputClass } from '@/components/ui/FormField';
import { useAddToCart } from '@/hooks/useCart';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { useCustomerAuth } from '@/hooks/useCustomerAuth';
import { useToggleWishlist, useWishlistIds } from '@/hooks/useWishlist';
import { useRatingSummary, useReviews, useSubmitReview } from '@/hooks/useReviews';
import { recordRecentlyViewed, useRecentlyViewed } from '@/hooks/useRecentlyViewed';
import { ApiError, getData, postVoid } from '@/lib/api';
import { formatDateTime, formatFileSize, formatPrice } from '@/lib/format';
import { setJsonLd, setPageMeta } from '@/lib/theme';
import type {
  PriceInfo,
  ProductBundle as ProductBundleType,
  ProductCard as ProductCardType,
  ProductDetail,
} from '@/types/api';

const STOCK_LABELS: Record<string, { label: string; className: string }> = {
  IN_STOCK: { label: 'Stokda var', className: 'text-[var(--color-success)]' },
  LOW_STOCK: { label: 'Son ədədlər', className: 'text-[var(--color-warning)]' },
  OUT_OF_STOCK: { label: 'Anbarda yoxdur', className: 'text-[var(--color-danger)]' },
  BACKORDER: { label: 'Sifarişlə gətirilir', className: 'text-[var(--color-subtle)]' },
};

const AVAILABILITY_SCHEMA: Record<string, string> = {
  IN_STOCK: 'https://schema.org/InStock',
  LOW_STOCK: 'https://schema.org/LimitedAvailability',
  OUT_OF_STOCK: 'https://schema.org/OutOfStock',
  BACKORDER: 'https://schema.org/BackOrder',
};

export function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { config, features } = useSiteConfig();
  const addToCart = useAddToCart();
  const navigate = useNavigate();
  const location = useLocation();
  const authStatus = useCustomerAuth((state) => state.status);
  const restore = useCustomerAuth((state) => state.restore);
  const wishlistIds = useWishlistIds();
  const toggleWishlist = useToggleWishlist();
  const toast = useToast();

  const [quantity, setQuantity] = useState(1);
  const [activeImage, setActiveImage] = useState(0);
  const [justAdded, setJustAdded] = useState(false);
  const [buyBarHidden, setBuyBarHidden] = useState(true);
  const [selectedOptions, setSelectedOptions] = useState<Record<number, number>>({});
  const buyRef = useRef<HTMLDivElement>(null);

  const productQuery = useQuery({
    queryKey: ['product', slug],
    queryFn: () => getData<ProductDetail>(`/public/products/${slug}`),
    enabled: Boolean(slug),
  });

  const relatedQuery = useQuery({
    queryKey: ['product', slug, 'related'],
    queryFn: () => getData<ProductCardType[]>(`/public/products/${slug}/related`, { limit: 4 }),
    enabled: Boolean(slug),
  });

  const product = productQuery.data;
  const recentlyViewed = useRecentlyViewed(product?.id);

  // Which attribute a given option belongs to, so picking "Qırmızı" can be filed under
  // the "Rəng" axis without the picker needing to know that mapping ahead of time.
  const optionToAttribute = useMemo(() => {
    const map = new Map<number, number>();
    product?.variantAttributes.forEach((attribute) => {
      attribute.options.forEach((option) => map.set(option.id, attribute.attributeId));
    });
    return map;
  }, [product]);

  const hasVariants = (product?.variantAttributes.length ?? 0) > 0;

  const matchedVariant = useMemo(() => {
    if (!product || !hasVariants) {
      return null;
    }
    const picked = Object.values(selectedOptions);
    if (picked.length !== product.variantAttributes.length) {
      return null;
    }
    return (
      product.variants.find(
        (variant) => variant.optionIds.length === picked.length
          && picked.every((id) => variant.optionIds.includes(id)),
      ) ?? null
    );
  }, [product, hasVariants, selectedOptions]);

  // A product's variants only settle in once the product itself has loaded, and a new
  // product means starting the picker over — defaulting to whichever variant the admin
  // marked default, or just the first one, so the page never opens looking incomplete.
  useEffect(() => {
    if (!product || product.variants.length === 0) {
      setSelectedOptions({});
      return;
    }
    const initial = product.variants.find((variant) => variant.isDefault) ?? product.variants[0]!;
    const next: Record<number, number> = {};
    initial.optionIds.forEach((optionId) => {
      const attributeId = optionToAttribute.get(optionId);
      if (attributeId !== undefined) {
        next[attributeId] = optionId;
      }
    });
    setSelectedOptions(next);
    // optionToAttribute is derived from `product` itself; re-running per product is enough.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product?.id]);

  useEffect(() => {
    if (!product) {
      return;
    }
    const mainImage = product.images.find((image) => image.main) ?? product.images[0];
    return setPageMeta(config, {
      title: product.seoTitle ?? product.name,
      description: product.seoDescription ?? product.shortDescription,
      ogImage: mainImage?.url,
    });
  }, [config, product]);

  // Logged once per visit, after the product has actually loaded — a 404 or a
  // still-pending fetch must not count as "viewed".
  useEffect(() => {
    if (product) {
      recordRecentlyViewed(product.id);
    }
  }, [product]);

  useEffect(() => {
    if (!product) {
      return;
    }
    const url = window.location.href;
    const availability = AVAILABILITY_SCHEMA[product.stockStatus];

    const breadcrumbItems = [
      { name: 'Ana səhifə', item: `${window.location.origin}/` },
      { name: 'Kataloq', item: `${window.location.origin}/kataloq` },
      ...(product.categorySlug && product.categoryName
        ? [{ name: product.categoryName, item: `${window.location.origin}/kataloq/${product.categorySlug}` }]
        : []),
      { name: product.name, item: url },
    ];

    return setJsonLd('product', [
      {
        '@context': 'https://schema.org',
        '@type': 'Product',
        name: product.name,
        description: product.shortDescription ?? product.seoDescription ?? undefined,
        sku: product.sku,
        image: product.images.map((image) => image.url),
        ...(product.showPrice && product.price.finalPrice !== null
          ? {
              offers: {
                '@type': 'Offer',
                url,
                priceCurrency: product.currency,
                price: product.price.finalPrice,
                availability,
              },
            }
          : {}),
      },
      {
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: breadcrumbItems.map((entry, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          name: entry.name,
          item: entry.item,
        })),
      },
    ]);
  }, [product]);
  // A different product means a different gallery and a fresh quantity.
  useEffect(() => {
    setActiveImage(0);
    setQuantity(1);
  }, [slug]);

  // The mobile sticky bar mirrors the real buy button, so it only needs to appear once
  // that button has scrolled out of view — no point doubling it up on screen.
  useEffect(() => {
    const target = buyRef.current;
    if (!target) {
      return;
    }
    const observer = new IntersectionObserver(([entry]) => setBuyBarHidden(entry!.isIntersecting), {
      rootMargin: '0px 0px -10% 0px',
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [product]);

  if (productQuery.isLoading) {
    return <ProductDetailSkeleton />;
  }

  if (!product) {
    return (
      <div className="container-site py-24 text-center">
        <h1 className="text-2xl font-bold">Məhsul tapılmadı</h1>
        <Link to="/kataloq" className="mt-4 inline-block text-[var(--color-primary)] hover:underline">
          Kataloqa qayıt
        </Link>
      </div>
    );
  }

  const variantReady = !hasVariants || matchedVariant !== null;
  const effectiveStockStatus = matchedVariant?.stockStatus ?? product.stockStatus;
  const effectiveAvailableQuantity = matchedVariant ? matchedVariant.availableQuantity : product.availableQuantity;
  const effectivePrice: PriceInfo = matchedVariant?.priceOverridden
    ? {
        listPrice: matchedVariant.price,
        finalPrice: matchedVariant.price,
        savingAmount: 0,
        savingPercentage: 0,
        discounted: false,
        discountId: null,
        discountName: null,
        badgeLabel: null,
        badgeStyle: null,
        badgeColor: null,
        showBadge: false,
        showCountdown: false,
        endsAt: null,
        currency: product.currency,
      }
    : product.price;
  const galleryImageUrl = matchedVariant?.imageUrl ?? product.images[activeImage]?.url;

  const stock = STOCK_LABELS[effectiveStockStatus] ?? STOCK_LABELS.IN_STOCK!;
  const maxQuantity = effectiveAvailableQuantity ?? product.maxOrderQuantity ?? 99;
  const canBuy = features.cartEnabled && product.purchasable && product.showPrice;

  const handleAdd = () => {
    if (hasVariants && !matchedVariant) {
      toast.error('Zəhmət olmasa variant seçin');
      return;
    }
    addToCart.mutate(
      { productId: product.id, quantity, variantId: matchedVariant?.id },
      {
        onSuccess: () => {
          setJustAdded(true);
          window.setTimeout(() => setJustAdded(false), 2500);
        },
      },
    );
  };

  const wishlisted = wishlistIds.has(product.id);

  const handleToggleWishlist = async () => {
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

  return (
    <div className={clsx('container-site py-8 lg:py-12', canBuy && 'pb-28 lg:pb-12')}>
      {/* ---------------- breadcrumb ---------------- */}
      <nav aria-label="Naviqasiya" className="mb-6 flex flex-wrap items-center gap-1.5 text-sm">
        <Link to="/" className="text-[var(--color-subtle)] hover:text-[var(--color-heading)]">
          Ana səhifə
        </Link>
        <ChevronRight size={14} className="text-[var(--color-subtle)]" aria-hidden="true" />
        <Link to="/kataloq" className="text-[var(--color-subtle)] hover:text-[var(--color-heading)]">
          Kataloq
        </Link>
        {product.categorySlug && (
          <>
            <ChevronRight size={14} className="text-[var(--color-subtle)]" aria-hidden="true" />
            <Link
              to={`/kataloq/${product.categorySlug}`}
              className="text-[var(--color-subtle)] hover:text-[var(--color-heading)]"
            >
              {product.categoryName}
            </Link>
          </>
        )}
      </nav>

      <div className="grid gap-10 lg:grid-cols-2">
        {/* ---------------- gallery ---------------- */}
        <div>
          <div className="relative overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]">
            <div className="aspect-square">
              {galleryImageUrl ? (
                <img
                  src={galleryImageUrl}
                  alt={product.images[activeImage]?.alt ?? product.name}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="flex h-full items-center justify-center text-[var(--color-subtle)]">
                  Şəkil yoxdur
                </div>
              )}
            </div>
          </div>

          {product.images.length > 1 && (
            <div className="mt-3 grid grid-cols-5 gap-2">
              {product.images.map((image, index) => (
                <button
                  key={image.id}
                  type="button"
                  onClick={() => setActiveImage(index)}
                  aria-label={`Şəkil ${index + 1}`}
                  aria-current={index === activeImage}
                  className={clsx(
                    'aspect-square overflow-hidden rounded-[var(--radius-button)] border-2 transition-colors',
                    index === activeImage
                      ? 'border-[var(--color-primary)]'
                      : 'border-transparent hover:border-[var(--color-border)]',
                  )}
                >
                  <img src={image.url} alt="" loading="lazy" className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* ---------------- info ---------------- */}
        <div>
          {product.categoryName && (
            <span className="text-sm font-semibold uppercase tracking-wide text-[var(--color-primary)]">
              {product.categoryName}
            </span>
          )}

          <h1 className="mt-2 text-3xl font-bold leading-tight tracking-tight lg:text-4xl">
            {product.name}
          </h1>

          {features.reviewsEnabled && product.reviewCount > 0 && (
            <a href="#reviews" className="mt-2 flex items-center gap-1.5 text-sm">
              <StarRow rating={product.averageRating} size={15} />
              <span className="text-[var(--color-subtle)]">
                {product.averageRating.toFixed(1)} ({product.reviewCount} rəy)
              </span>
            </a>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-[var(--color-subtle)]">
            <span>SKU: {matchedVariant?.sku ?? product.sku}</span>
            {features.showStockStatus && (
              <span className={clsx('font-medium', stock.className)}>{stock.label}</span>
            )}
          </div>

          <ShareButtons title={product.name} toast={toast} />

          {product.shortDescription && (
            <p className="mt-5 text-base leading-relaxed text-[var(--color-body)]">
              {product.shortDescription}
            </p>
          )}

          {/* ---- price ---- */}
          <div className="mt-7 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)] p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <PriceDisplay price={effectivePrice} showPrice={product.showPrice} size="xl" />
              {effectivePrice.discounted && <DiscountPill price={effectivePrice} />}
            </div>

            {effectivePrice.showCountdown && effectivePrice.endsAt && (
              <div className="mt-4 border-t border-[var(--color-border)] pt-4">
                <p className="mb-2 text-sm font-medium text-[var(--color-heading)]">
                  Kampaniyanın bitməsinə:
                </p>
                <CountdownTimer endsAt={effectivePrice.endsAt} variant="blocks" />
              </div>
            )}
          </div>

          {/* ---- variants ---- */}
          {hasVariants && (
            <div className="mt-6 space-y-4">
              {product.variantAttributes.map((attribute) => (
                <div key={attribute.attributeId}>
                  <p className="mb-2 text-sm font-semibold text-[var(--color-heading)]">
                    {attribute.name}
                    {selectedOptions[attribute.attributeId] && (
                      <span className="ms-1.5 font-normal text-[var(--color-subtle)]">
                        —{' '}
                        {
                          attribute.options.find(
                            (option) => option.id === selectedOptions[attribute.attributeId],
                          )?.label
                        }
                      </span>
                    )}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {attribute.options.map((option) => {
                      const active = selectedOptions[attribute.attributeId] === option.id;
                      return option.colorHex ? (
                        <button
                          key={option.id}
                          type="button"
                          onClick={() =>
                            setSelectedOptions((current) => ({ ...current, [attribute.attributeId]: option.id }))
                          }
                          aria-label={option.label}
                          aria-pressed={active}
                          title={option.label}
                          className={clsx(
                            'h-8 w-8 rounded-full border-2 transition-transform',
                            active ? 'border-[var(--color-primary)] scale-110' : 'border-[var(--color-border)]',
                          )}
                          style={{ backgroundColor: option.colorHex }}
                        />
                      ) : (
                        <button
                          key={option.id}
                          type="button"
                          onClick={() =>
                            setSelectedOptions((current) => ({ ...current, [attribute.attributeId]: option.id }))
                          }
                          aria-pressed={active}
                          className={clsx(
                            'rounded-[var(--radius-button)] border px-3.5 py-2 text-sm font-medium transition-colors',
                            active
                              ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                              : 'border-[var(--color-border)] text-[var(--color-body)] hover:border-[var(--color-primary)]/50',
                          )}
                        >
                          {option.label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* ---- buy ---- */}
          {canBuy ? (
            <div ref={buyRef} className="mt-6 flex flex-wrap items-stretch gap-3">
              <div className="flex items-center rounded-[var(--radius-button)] border border-[var(--color-border)]">
                <button
                  type="button"
                  onClick={() => setQuantity((q) => Math.max(product.minOrderQuantity, q - 1))}
                  disabled={quantity <= product.minOrderQuantity}
                  aria-label="Azalt"
                  className="px-3.5 py-3 text-[var(--color-body)] disabled:opacity-40"
                >
                  <Minus size={16} aria-hidden="true" />
                </button>
                <span className="min-w-10 text-center font-semibold tabular">{quantity}</span>
                <button
                  type="button"
                  onClick={() => setQuantity((q) => Math.min(maxQuantity, q + 1))}
                  disabled={quantity >= maxQuantity}
                  aria-label="Artır"
                  className="px-3.5 py-3 text-[var(--color-body)] disabled:opacity-40"
                >
                  <Plus size={16} aria-hidden="true" />
                </button>
              </div>

              <button
                type="button"
                onClick={handleAdd}
                disabled={addToCart.isPending || effectiveStockStatus === 'OUT_OF_STOCK' || !variantReady}
                className={clsx(
                  'flex flex-1 items-center justify-center gap-2 rounded-[var(--radius-button)] px-6 py-3',
                  'text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60',
                  justAdded
                    ? 'bg-[var(--color-success)] text-white'
                    : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]',
                )}
              >
                {addToCart.isPending ? (
                  <Loader2 size={17} className="animate-spin" aria-hidden="true" />
                ) : justAdded ? (
                  <Check size={17} aria-hidden="true" />
                ) : (
                  <ShoppingCart size={17} aria-hidden="true" />
                )}
                {justAdded ? 'Səbətə əlavə edildi' : !variantReady ? 'Variant seçin' : 'Səbətə at'}
              </button>

              {features.wishlistEnabled && (
                <button
                  type="button"
                  onClick={() => void handleToggleWishlist()}
                  aria-label={wishlisted ? 'Sevimlilərdən çıxar' : 'Sevimlilərə əlavə et'}
                  aria-pressed={wishlisted}
                  className={clsx(
                    'flex items-center justify-center rounded-[var(--radius-button)] border px-3.5 py-3 transition-colors',
                    wishlisted
                      ? 'border-[var(--color-danger)] bg-[var(--color-danger)]/10'
                      : 'border-[var(--color-border)] hover:bg-[var(--color-muted)]',
                  )}
                >
                  <Heart
                    size={18}
                    aria-hidden="true"
                    className={clsx(
                      wishlisted ? 'fill-[var(--color-danger)] text-[var(--color-danger)]' : 'text-[var(--color-body)]',
                    )}
                  />
                </button>
              )}
            </div>
          ) : null}

          {canBuy && effectiveStockStatus === 'OUT_OF_STOCK' && (
            <StockNotifyForm slug={product.slug} toast={toast} />
          )}

          {!canBuy && (
            <div className="mt-6 flex flex-wrap items-stretch gap-3">
              <Link
                to="/elaqe"
                className="inline-flex items-center rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)]"
              >
                Qiymət üçün müraciət edin
              </Link>
              {features.wishlistEnabled && (
                <button
                  type="button"
                  onClick={() => void handleToggleWishlist()}
                  aria-label={wishlisted ? 'Sevimlilərdən çıxar' : 'Sevimlilərə əlavə et'}
                  aria-pressed={wishlisted}
                  className={clsx(
                    'flex items-center justify-center rounded-[var(--radius-button)] border px-3.5 py-3 transition-colors',
                    wishlisted
                      ? 'border-[var(--color-danger)] bg-[var(--color-danger)]/10'
                      : 'border-[var(--color-border)] hover:bg-[var(--color-muted)]',
                  )}
                >
                  <Heart
                    size={18}
                    aria-hidden="true"
                    className={clsx(
                      wishlisted ? 'fill-[var(--color-danger)] text-[var(--color-danger)]' : 'text-[var(--color-body)]',
                    )}
                  />
                </button>
              )}
            </div>
          )}

          {/* ---- documents ---- */}
          {product.documents.length > 0 && (
            <div className="mt-8">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--color-heading)]">
                Sənədlər
              </h2>
              <ul className="space-y-2">
                {product.documents.map((document) => (
                  <li key={document.id}>
                    <a
                      href={document.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-3 rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-3 text-sm transition-colors hover:bg-[var(--color-muted)]"
                    >
                      <FileText size={17} className="text-[var(--color-primary)]" aria-hidden="true" />
                      <span className="flex-1 font-medium">{document.title}</span>
                      <span className="text-xs text-[var(--color-subtle)]">
                        {formatFileSize(document.fileSize)}
                      </span>
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>

      {/* ---------------- description & specs ---------------- */}
      {(product.description || product.specifications.length > 0) && (
        <div className="mt-14 grid gap-10 lg:grid-cols-2">
          {product.description && (
            <section>
              <h2 className="mb-4 text-xl font-bold">Təsvir</h2>
              {/*
                Server-sanitised on write (OWASP HTML sanitizer), so this is safe to
                render as markup. Nothing unsanitised ever reaches the database.
              */}
              <div
                className="prose-content"
                dangerouslySetInnerHTML={{ __html: product.description }}
              />
            </section>
          )}

          {product.specifications.length > 0 && (
            <section>
              <h2 className="mb-4 text-xl font-bold">Xüsusiyyətlər</h2>
              {product.specifications.map((group) => (
                <div key={group.groupCode} className="mb-6">
                  <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                    {group.groupName}
                  </h3>
                  <dl className="overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)]">
                    {group.lines.map((line, index) => (
                      <div
                        key={line.attributeCode}
                        className={clsx(
                          'flex items-baseline justify-between gap-4 px-4 py-3 text-sm',
                          index % 2 === 1 && 'bg-[var(--color-muted)]',
                        )}
                      >
                        <dt className="text-[var(--color-body)]">{line.label}</dt>
                        <dd className="text-end font-medium text-[var(--color-heading)]">
                          {line.value}
                          {line.unit && <span className="ms-1 text-[var(--color-subtle)]">{line.unit}</span>}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </div>
              ))}
            </section>
          )}
        </div>
      )}

      {/* ---------------- bundle ---------------- */}
      <BundleSection slug={product.slug} toast={toast} />

      {/* ---------------- related ---------------- */}
      {relatedQuery.data && relatedQuery.data.length > 0 && (
        <section className="mt-16">
          <h2 className="mb-6 text-2xl font-bold tracking-tight">Oxşar məhsullar</h2>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {relatedQuery.data.map((related) => (
              <ProductCard key={related.id} product={related} />
            ))}
          </div>
        </section>
      )}

      {/* ---------------- recently viewed ---------------- */}
      {(recentlyViewed.isLoading || recentlyViewed.products.length > 0) && (
        <section className="mt-16">
          <h2 className="mb-6 text-2xl font-bold tracking-tight">Son baxdıqlarınız</h2>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {recentlyViewed.isLoading
              ? Array.from({ length: 4 }).map((_, index) => <ProductCardSkeleton key={index} />)
              : recentlyViewed.products.map((viewed) => (
                  <ProductCard key={viewed.id} product={viewed} />
                ))}
          </div>
        </section>
      )}

      {/* ---------------- reviews ---------------- */}
      {features.reviewsEnabled && (
        <section id="reviews" className="mt-16 scroll-mt-24">
          <ReviewsSection product={product} toast={toast} />
        </section>
      )}

      {/* ---------------- mobile sticky buy bar ---------------- */}
      {canBuy && (
        <div
          className={clsx(
            'fixed inset-x-0 bottom-0 z-40 border-t border-[var(--color-border)] bg-[var(--color-canvas)]/95',
            'px-4 py-3 shadow-[var(--shadow-popover)] backdrop-blur-md transition-transform duration-200 lg:hidden',
            'pb-[calc(0.75rem+env(safe-area-inset-bottom))]',
            buyBarHidden ? 'translate-y-full' : 'translate-y-0',
          )}
        >
          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs text-[var(--color-subtle)]">{product.name}</p>
              <PriceDisplay price={effectivePrice} showPrice={product.showPrice} size="sm" />
            </div>
            <button
              type="button"
              onClick={handleAdd}
              disabled={addToCart.isPending || effectiveStockStatus === 'OUT_OF_STOCK' || !variantReady}
              className={clsx(
                'flex shrink-0 items-center justify-center gap-2 rounded-[var(--radius-button)] px-5 py-2.5',
                'text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60',
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
              {justAdded ? 'Əlavə edildi' : 'Səbətə at'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** Matches the real page's two-column shape so nothing jumps once data lands. */
function ProductDetailSkeleton() {
  return (
    <div className="container-site py-8 lg:py-12">
      <div className="mb-6 flex gap-2">
        <div className="skeleton h-4 w-16" />
        <div className="skeleton h-4 w-4" />
        <div className="skeleton h-4 w-20" />
      </div>

      <div className="grid gap-10 lg:grid-cols-2">
        <div>
          <div className="skeleton aspect-square rounded-[var(--radius-card)]" />
          <div className="mt-3 grid grid-cols-5 gap-2">
            {Array.from({ length: 5 }).map((_, index) => (
              <div key={index} className="skeleton aspect-square rounded-[var(--radius-button)]" />
            ))}
          </div>
        </div>

        <div>
          <div className="skeleton h-4 w-24" />
          <div className="skeleton mt-3 h-9 w-full" />
          <div className="skeleton mt-2 h-9 w-2/3" />
          <div className="skeleton mt-4 h-4 w-40" />
          <div className="skeleton mt-5 h-16 w-full" />
          <div className="skeleton mt-7 h-24 w-full rounded-[var(--radius-card)]" />
          <div className="mt-6 flex gap-3">
            <div className="skeleton h-12 w-28 rounded-[var(--radius-button)]" />
            <div className="skeleton h-12 flex-1 rounded-[var(--radius-button)]" />
          </div>
        </div>
      </div>
    </div>
  );
}

function StockNotifyForm({ slug, toast }: { slug: string; toast: ReturnType<typeof useToast> }) {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);

  const subscribe = useMutation({
    mutationFn: () => postVoid(`/public/products/${slug}/notify-stock`, { email }),
    onSuccess: () => setSent(true),
    onError: (error: unknown) =>
      toast.error(error instanceof ApiError ? error.message : 'Abunə olmaq mümkün olmadı'),
  });

  if (sent) {
    return (
      <p className="mt-6 rounded-[var(--radius-card)] border border-[var(--color-success)]/30 bg-[var(--color-success)]/5 px-4 py-3 text-sm text-[var(--color-success)]">
        Qeyd edildi — məhsul stoka gələndə sizə xəbər verəcəyik.
      </p>
    );
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        subscribe.mutate();
      }}
      className="mt-6 flex flex-wrap gap-2"
    >
      <input
        type="email"
        required
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        placeholder="E-poçt ünvanınız"
        className={clsx(inputClass(false), 'flex-1')}
      />
      <button
        type="submit"
        disabled={subscribe.isPending}
        className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2.5 text-sm font-semibold text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] disabled:opacity-60"
      >
        Mövcud olduqda bildir
      </button>
    </form>
  );
}

/** Plain share links — no SDK, no tracking pixel, just the platforms' own share URLs. */
function ShareButtons({ title, toast }: { title: string; toast: ReturnType<typeof useToast> }) {
  const url = window.location.href;

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(url);
      toast.success('Link kopyalandı');
    } catch {
      toast.error('Link kopyalanmadı');
    }
  };

  const links = [
    {
      label: 'Facebook',
      icon: Facebook,
      href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`,
    },
    {
      label: 'WhatsApp',
      icon: MessageCircle,
      href: `https://wa.me/?text=${encodeURIComponent(`${title} — ${url}`)}`,
    },
    {
      label: 'Telegram',
      icon: Send,
      href: `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(title)}`,
    },
  ];

  return (
    <div className="mt-4 flex items-center gap-2">
      <span className="text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
        Paylaş
      </span>
      {links.map((link) => (
        <a
          key={link.label}
          href={link.href}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`${link.label}-da paylaş`}
          className="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--color-border)] text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
        >
          <link.icon size={15} aria-hidden="true" />
        </a>
      ))}
      <button
        type="button"
        onClick={() => void copyLink()}
        aria-label="Linki kopyala"
        className="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--color-border)] text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
      >
        <Link2 size={15} aria-hidden="true" />
      </button>
    </div>
  );
}

/** Five stars, filled up to the nearest whole star. Decorative — the number beside it
 * already says the exact average, so a screen reader hearing "star star star" adds noise. */
function StarRow({ rating, size = 16 }: { rating: number; size?: number }) {
  return (
    <div className="flex items-center gap-0.5" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((star) => (
        <Star
          key={star}
          size={size}
          className={
            star <= Math.round(rating)
              ? 'fill-[var(--color-warning)] text-[var(--color-warning)]'
              : 'text-[var(--color-border)]'
          }
        />
      ))}
    </div>
  );
}

function ReviewsSection({ product, toast }: { product: ProductDetail; toast: ReturnType<typeof useToast> }) {
  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const authStatus = useCustomerAuth((state) => state.status);
  const restore = useCustomerAuth((state) => state.restore);

  const summary = useRatingSummary(product.slug);
  const reviews = useReviews(product.slug, page);
  const submit = useSubmitReview(product.slug);

  const handleWriteReview = async () => {
    let status = authStatus;
    if (status === 'idle') {
      await restore();
      status = useCustomerAuth.getState().status;
    }
    if (status !== 'authenticated') {
      navigate(`/hesab/giris?next=${encodeURIComponent(location.pathname)}#reviews`);
      return;
    }
    setFormOpen(true);
  };

  const data = summary.data;

  return (
    <div className="grid gap-8 lg:grid-cols-[280px_1fr]">
      {/* ---- summary ---- */}
      <div>
        <h2 className="mb-4 text-2xl font-bold tracking-tight">Rəylər</h2>

        {data && data.count > 0 ? (
          <div className="card p-5">
            <div className="flex items-baseline gap-2">
              <span className="font-display text-4xl font-bold tabular">{data.average.toFixed(1)}</span>
              <span className="text-sm text-[var(--color-subtle)]">/ 5</span>
            </div>
            <StarRow rating={data.average} size={18} />
            <p className="mt-1 text-sm text-[var(--color-subtle)]">{data.count} rəy</p>

            <div className="mt-4 space-y-1.5">
              {[5, 4, 3, 2, 1].map((star) => {
                const count = data.breakdown[star] ?? 0;
                const percentage = data.count > 0 ? (count / data.count) * 100 : 0;
                return (
                  <div key={star} className="flex items-center gap-2 text-xs">
                    <span className="w-9 shrink-0 text-[var(--color-subtle)]">{star} ulduz</span>
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--color-muted)]">
                      <div
                        className="h-full bg-[var(--color-warning)]"
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                    <span className="w-5 shrink-0 text-end tabular text-[var(--color-subtle)]">{count}</span>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          <p className="text-sm text-[var(--color-subtle)]">Hələ rəy yoxdur. İlk rəyi siz yazın.</p>
        )}

        {!formOpen && (
          <button
            type="button"
            onClick={() => void handleWriteReview()}
            className="mt-4 w-full rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2.5 text-sm font-semibold text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
          >
            Rəy yaz
          </button>
        )}

        {formOpen && (
          <ReviewForm
            pending={submit.isPending}
            onCancel={() => setFormOpen(false)}
            onSubmit={(values) => {
              submit.mutate(
                { productId: product.id, ...values },
                {
                  onSuccess: () => {
                    toast.success('Rəyiniz göndərildi — təsdiqləndikdən sonra görünəcək');
                    setFormOpen(false);
                  },
                  onError: (error) => {
                    toast.error(error instanceof ApiError ? error.message : 'Rəy göndərilmədi');
                  },
                },
              );
            }}
          />
        )}
      </div>

      {/* ---- list ---- */}
      <div>
        {reviews.isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="skeleton h-24 w-full rounded-[var(--radius-card)]" />
            ))}
          </div>
        ) : !reviews.data || reviews.data.content.length === 0 ? (
          <div className="card flex flex-col items-center gap-2 p-10 text-center text-[var(--color-subtle)]">
            <Star size={26} aria-hidden="true" />
            <p className="text-sm">Bu məhsul üçün hələ təsdiqlənmiş rəy yoxdur</p>
          </div>
        ) : (
          <>
            <ul className="space-y-4">
              {reviews.data.content.map((review) => (
                <li key={review.id} className="card p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <StarRow rating={review.rating} />
                      <span className="font-medium text-[var(--color-heading)]">{review.authorName}</span>
                      {review.verifiedPurchase && (
                        <span className="rounded-full bg-[var(--color-success)]/10 px-2 py-0.5 text-[0.6875rem] font-semibold text-[var(--color-success)]">
                          Təsdiqlənmiş alış
                        </span>
                      )}
                    </div>
                    <span className="text-xs text-[var(--color-subtle)] tabular">
                      {formatDateTime(review.createdAt)}
                    </span>
                  </div>
                  {review.title && (
                    <p className="mt-2 font-semibold text-[var(--color-heading)]">{review.title}</p>
                  )}
                  {review.body && (
                    <p className="mt-1 text-sm leading-relaxed text-[var(--color-body)]">{review.body}</p>
                  )}
                  {review.adminReply && (
                    <div className="mt-3 rounded-[var(--radius-button)] bg-[var(--color-muted)] p-3 text-sm">
                      <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                        Satıcının cavabı
                      </p>
                      <p className="text-[var(--color-body)]">{review.adminReply}</p>
                    </div>
                  )}
                </li>
              ))}
            </ul>

            {reviews.data.totalPages > 1 && (
              <div className="mt-5 flex items-center justify-between">
                <button
                  type="button"
                  disabled={reviews.data.first}
                  onClick={() => setPage((current) => current - 1)}
                  className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-1.5 text-sm disabled:opacity-40"
                >
                  Əvvəlki
                </button>
                <span className="text-xs text-[var(--color-subtle)]">
                  {reviews.data.page + 1} / {reviews.data.totalPages}
                </span>
                <button
                  type="button"
                  disabled={reviews.data.last}
                  onClick={() => setPage((current) => current + 1)}
                  className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-1.5 text-sm disabled:opacity-40"
                >
                  Növbəti
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function ReviewForm({
  pending,
  onCancel,
  onSubmit,
}: {
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: { rating: number; title?: string; body?: string }) => void;
}) {
  const [rating, setRating] = useState(5);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  return (
    <div className="card mt-4 p-4">
      <p className="mb-2 text-sm font-medium text-[var(--color-heading)]">Qiymətləndirmə</p>
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            onClick={() => setRating(star)}
            aria-label={`${star} ulduz`}
            className="p-0.5"
          >
            <Star
              size={22}
              className={
                star <= rating
                  ? 'fill-[var(--color-warning)] text-[var(--color-warning)]'
                  : 'text-[var(--color-border)]'
              }
            />
          </button>
        ))}
      </div>

      <Field label="Başlıq (istəyə bağlı)" className="mt-3">
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
          className={inputClass(false)}
        />
      </Field>

      <Field label="Rəyiniz (istəyə bağlı)" className="mt-3">
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          rows={3}
          maxLength={4000}
          className={inputClass(false)}
        />
      </Field>

      <div className="mt-4 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-[var(--radius-button)] px-4 py-2 text-sm font-medium text-[var(--color-body)] hover:bg-[var(--color-muted)]"
        >
          Ləğv et
        </button>
        <button
          type="button"
          disabled={pending}
          onClick={() => onSubmit({ rating, title: title.trim() || undefined, body: body.trim() || undefined })}
          className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-[var(--color-primary-contrast)] disabled:opacity-60"
        >
          Göndər
        </button>
      </div>
    </div>
  );
}

/** "Frequently bought together" — an admin-curated cross-sell, not a priced bundle; the
 * combined total is always the live sum of each item's own resolved price. */
function BundleSection({ slug, toast }: { slug: string; toast: ReturnType<typeof useToast> }) {
  const bundleQuery = useQuery({
    queryKey: ['product', slug, 'bundle'],
    queryFn: () => getData<ProductBundleType | null>(`/public/products/${slug}/bundle`),
  });
  const addToCart = useAddToCart();
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [adding, setAdding] = useState(false);

  const bundle = bundleQuery.data;

  useEffect(() => {
    if (bundle) {
      setSelected(new Set(bundle.items.map((item) => item.id)));
    }
  }, [bundle]);

  if (!bundle || bundle.items.length === 0) {
    return null;
  }

  const selectedItems = bundle.items.filter((item) => selected.has(item.id));
  const total = selectedItems.reduce((sum, item) => sum + (item.price.finalPrice ?? 0), 0);

  const addSelected = async () => {
    setAdding(true);
    try {
      for (const item of selectedItems) {
        await new Promise<void>((resolve, reject) => {
          addToCart.mutate(
            { productId: item.id, quantity: 1 },
            { onSuccess: () => resolve(), onError: () => reject() },
          );
        });
      }
      toast.success('Seçilmiş məhsullar səbətə əlavə edildi');
    } catch {
      toast.error('Bəzi məhsullar səbətə əlavə edilmədi');
    } finally {
      setAdding(false);
    }
  };

  return (
    <section className="mt-16">
      <h2 className="mb-6 text-2xl font-bold tracking-tight">Birlikdə tez-tez alınır</h2>
      <div className="card flex flex-col gap-4 p-5 lg:flex-row lg:items-center">
        <ul className="grid flex-1 grid-cols-2 gap-4 sm:grid-cols-4">
          {bundle.items.map((item) => (
            <li key={item.id}>
              <label className="flex cursor-pointer flex-col items-center gap-2 text-center">
                <input
                  type="checkbox"
                  checked={selected.has(item.id)}
                  onChange={(event) =>
                    setSelected((current) => {
                      const next = new Set(current);
                      if (event.target.checked) {
                        next.add(item.id);
                      } else {
                        next.delete(item.id);
                      }
                      return next;
                    })
                  }
                  className="h-4 w-4 rounded accent-[var(--color-primary)]"
                />
                <div className="aspect-square w-full overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
                  {item.imageUrl && (
                    <img src={item.imageUrl} alt="" className="h-full w-full object-cover" />
                  )}
                </div>
                <Link
                  to={`/mehsul/${item.slug}`}
                  className="line-clamp-2 text-xs font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                >
                  {item.name}
                </Link>
                <PriceDisplay price={item.price} showPrice={item.showPrice} size="sm" showSaving={false} />
              </label>
            </li>
          ))}
        </ul>

        <div className="shrink-0 border-t border-[var(--color-border)] pt-4 text-center lg:w-52 lg:border-t-0 lg:border-s lg:ps-5 lg:pt-0">
          <p className="text-xs text-[var(--color-subtle)]">Seçilənlərin cəmi</p>
          <p className="font-display text-2xl font-bold tabular">{formatPrice(total)}</p>
          <button
            type="button"
            onClick={() => void addSelected()}
            disabled={selectedItems.length === 0 || adding}
            className="mt-3 w-full rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
          >
            {adding ? 'Əlavə edilir…' : 'Seçilənləri səbətə at'}
          </button>
        </div>
      </div>
    </section>
  );
}
