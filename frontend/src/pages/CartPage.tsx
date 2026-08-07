import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import clsx from 'clsx';
import { AlertCircle, FileText, Minus, Plus, ShoppingBag, Tag, Trash2, Truck, X } from 'lucide-react';
import { InlinePrice } from '@/components/catalog/PriceDisplay';
import { DiscountPill } from '@/components/catalog/DiscountRibbon';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import {
  useApplyPromoCode,
  useCart,
  useRemoveCartItem,
  useRemovePromoCode,
  useUpdateCartItem,
} from '@/hooks/useCart';
import { useCustomerAuth } from '@/hooks/useCustomerAuth';
import { ApiError, postData } from '@/lib/api';
import { formatPrice } from '@/lib/format';
import type { CartNotice } from '@/types/api';

const NOTICE_TONE: Record<string, string> = {
  PRICE_DROPPED: 'border-[var(--color-success)] bg-[var(--color-success)]/10',
  PRICE_INCREASED: 'border-[var(--color-warning)] bg-[var(--color-warning)]/10',
  QUANTITY_REDUCED: 'border-[var(--color-warning)] bg-[var(--color-warning)]/10',
  OUT_OF_STOCK: 'border-[var(--color-danger)] bg-[var(--color-danger)]/10',
  PRODUCT_UNAVAILABLE: 'border-[var(--color-danger)] bg-[var(--color-danger)]/10',
  PROMO_REMOVED: 'border-[var(--color-warning)] bg-[var(--color-warning)]/10',
  MIN_ORDER_NOT_MET: 'border-[var(--color-warning)] bg-[var(--color-warning)]/10',
};

export default function CartPage() {
  const { data: cart, isLoading } = useCart();
  const updateItem = useUpdateCartItem();
  const removeItem = useRemoveCartItem();
  const applyPromo = useApplyPromoCode();
  const removePromo = useRemovePromoCode();
  const [promoInput, setPromoInput] = useState('');
  const [quoteModalOpen, setQuoteModalOpen] = useState(false);
  const { customer } = useCustomerAuth();

  if (isLoading) {
    return <SectionLoader label="Səbət yüklənir…" />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="container-site flex flex-col items-center py-24 text-center">
        <ShoppingBag size={44} className="mb-5 text-[var(--color-subtle)]" aria-hidden="true" />
        <h1 className="mb-2 text-2xl font-bold">Səbətiniz boşdur</h1>
        <p className="mb-7 max-w-sm text-[var(--color-body)]">
          Kataloqumuzu nəzərdən keçirin və bəyəndiyiniz məhsulları səbətə əlavə edin.
        </p>
        <Link
          to="/kataloq"
          className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)]"
        >
          Kataloqa bax
        </Link>
      </div>
    );
  }

  const { totals } = cart;

  return (
    <div className="container-site py-8 lg:py-12">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">Səbət</h1>

      {/* Notices are the server telling the shopper something changed underneath
          them — a campaign started, stock ran short. They are shown at the top so
          nobody reaches checkout surprised. */}
      {cart.notices.length > 0 && (
        <div className="mb-6 space-y-2">
          {cart.notices.map((notice, index) => (
            <NoticeBanner key={`${notice.type}-${index}`} notice={notice} />
          ))}
        </div>
      )}

      <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
        {/* ---------------- lines ---------------- */}
        <ul className="space-y-3">
          {cart.items.map((line) => (
            <li
              key={line.id}
              className={clsx('card flex gap-4 p-4', !line.available && 'opacity-60')}
            >
              <Link
                to={`/mehsul/${line.slug}`}
                className="h-24 w-24 shrink-0 overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]"
              >
                {line.imageUrl && (
                  <img
                    src={line.imageUrl}
                    alt={line.name}
                    loading="lazy"
                    className="h-full w-full object-cover"
                  />
                )}
              </Link>

              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <Link
                      to={`/mehsul/${line.slug}`}
                      className="line-clamp-2 font-semibold text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                    >
                      {line.name}
                    </Link>
                    {line.variantLabel && (
                      <p className="mt-0.5 text-xs text-[var(--color-body)]">{line.variantLabel}</p>
                    )}
                    <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                      SKU: {line.variantSku ?? line.sku}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => removeItem.mutate(line.id)}
                    aria-label={`${line.name} məhsulunu səbətdən çıxar`}
                    className="shrink-0 rounded p-1.5 text-[var(--color-subtle)] transition-colors hover:bg-[var(--color-muted)] hover:text-[var(--color-danger)]"
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                </div>

                {line.discounted && (
                  <div className="flex items-center gap-2">
                    <DiscountPill
                      price={{
                        discounted: true,
                        badgeLabel: line.badgeLabel ?? `-${line.savingPercentage}%`,
                        badgeStyle: 'SALE',
                        badgeColor: null,
                      }}
                    />
                    {line.discountName && (
                      <span className="text-xs text-[var(--color-subtle)]">{line.discountName}</span>
                    )}
                  </div>
                )}

                <div className="mt-auto flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center rounded-[var(--radius-button)] border border-[var(--color-border)]">
                    <button
                      type="button"
                      onClick={() =>
                        updateItem.mutate({ itemId: line.id, quantity: line.quantity - 1 })
                      }
                      disabled={updateItem.isPending}
                      aria-label="Azalt"
                      className="px-3 py-2 text-[var(--color-body)] disabled:opacity-40"
                    >
                      <Minus size={14} aria-hidden="true" />
                    </button>
                    <span className="min-w-8 text-center text-sm font-semibold tabular">
                      {line.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() =>
                        updateItem.mutate({ itemId: line.id, quantity: line.quantity + 1 })
                      }
                      disabled={
                        updateItem.isPending ||
                        (line.availableQuantity !== null && line.quantity >= line.availableQuantity)
                      }
                      aria-label="Artır"
                      className="px-3 py-2 text-[var(--color-body)] disabled:opacity-40"
                    >
                      <Plus size={14} aria-hidden="true" />
                    </button>
                  </div>

                  <div className="text-end">
                    <InlinePrice
                      unitPrice={line.unitPrice}
                      originalUnitPrice={line.originalUnitPrice}
                    />
                    {line.quantity > 1 && (
                      <p className="text-sm font-bold text-[var(--color-heading)] tabular">
                        {formatPrice(line.lineTotal)}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>

        {/* ---------------- summary ---------------- */}
        <aside className="lg:sticky lg:top-[calc(var(--header-height)+1rem)] lg:self-start">
          <div className="card p-5">
            <h2 className="mb-4 text-lg font-bold">Sifariş xülasəsi</h2>

            {/* Promo code */}
            {cart.promo ? (
              <div className="mb-4 flex items-center justify-between gap-2 rounded-[var(--radius-button)] border border-[var(--color-success)] bg-[var(--color-success)]/10 px-3 py-2.5">
                <span className="flex items-center gap-2 text-sm font-medium text-[var(--color-success)]">
                  <Tag size={14} aria-hidden="true" />
                  {cart.promo.code}
                </span>
                <button
                  type="button"
                  onClick={() => removePromo.mutate()}
                  aria-label="Promo kodu ləğv et"
                  className="text-[var(--color-subtle)] hover:text-[var(--color-danger)]"
                >
                  <X size={15} aria-hidden="true" />
                </button>
              </div>
            ) : (
              <form
                className="mb-4 flex gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (promoInput.trim()) {
                    applyPromo.mutate(promoInput.trim(), { onSuccess: () => setPromoInput('') });
                  }
                }}
              >
                <label htmlFor="promo" className="sr-only">
                  Promo kod
                </label>
                <input
                  id="promo"
                  value={promoInput}
                  onChange={(event) => setPromoInput(event.target.value.toUpperCase())}
                  placeholder="Promo kod"
                  className="min-w-0 flex-1 rounded-[var(--radius-input)] border border-[var(--color-border)] px-3 py-2 text-sm uppercase outline-none focus:border-[var(--color-primary)]"
                />
                <button
                  type="submit"
                  disabled={applyPromo.isPending || !promoInput.trim()}
                  className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2 text-sm font-semibold disabled:opacity-40"
                >
                  Tətbiq et
                </button>
              </form>
            )}

            <dl className="space-y-2.5 border-t border-[var(--color-border)] pt-4 text-sm">
              <Row label="Məhsullar" value={formatPrice(totals.originalSubtotal)} />

              {totals.itemDiscountTotal > 0 && (
                <Row
                  label="Kampaniya endirimi"
                  value={`− ${formatPrice(totals.itemDiscountTotal)}`}
                  tone="success"
                />
              )}

              {totals.promoDiscount > 0 && (
                <Row
                  label="Promo kod"
                  value={`− ${formatPrice(totals.promoDiscount)}`}
                  tone="success"
                />
              )}

              <Row
                label="Çatdırılma"
                value={totals.freeDelivery ? 'Pulsuz' : formatPrice(totals.deliveryPrice)}
                tone={totals.freeDelivery ? 'success' : undefined}
              />
            </dl>

            {totals.amountToFreeDelivery !== null && totals.amountToFreeDelivery > 0 && (
              <p className="mt-3 flex items-start gap-2 rounded-[var(--radius-button)] bg-[var(--color-muted)] px-3 py-2.5 text-xs text-[var(--color-body)]">
                <Truck size={14} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
                <span>
                  Pulsuz çatdırılma üçün daha{' '}
                  <strong className="text-[var(--color-heading)]">
                    {formatPrice(totals.amountToFreeDelivery)}
                  </strong>{' '}
                  əlavə edin
                </span>
              </p>
            )}

            <div className="mt-4 flex items-baseline justify-between border-t border-[var(--color-border)] pt-4">
              <span className="font-semibold">Yekun</span>
              <span className="font-display text-2xl font-bold tabular text-[var(--color-heading)]">
                {formatPrice(totals.total)}
              </span>
            </div>

            {totals.totalSaving > 0 && (
              <p className="mt-1.5 text-end text-sm font-medium text-[var(--color-success)]">
                {formatPrice(totals.totalSaving)} qənaət etdiniz
              </p>
            )}

            <Link
              to="/checkout"
              aria-disabled={!cart.checkoutAllowed}
              className={clsx(
                'mt-5 block rounded-[var(--radius-button)] px-6 py-3 text-center text-sm font-semibold transition-colors',
                cart.checkoutAllowed
                  ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]'
                  : 'pointer-events-none bg-[var(--color-muted)] text-[var(--color-subtle)]',
              )}
            >
              Sifarişi tamamla
            </Link>

            <Link
              to="/kataloq"
              className="mt-3 block text-center text-sm font-medium text-[var(--color-primary)] hover:underline"
            >
              Alış-verişə davam et
            </Link>

            {customer && (
              <button
                type="button"
                onClick={() => setQuoteModalOpen(true)}
                className="mt-3 flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2.5 text-sm font-medium text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]"
              >
                <FileText size={15} aria-hidden="true" />
                Böyük sifariş üçün təklif istə
              </button>
            )}
          </div>
        </aside>
      </div>

      {quoteModalOpen && (
        <QuoteRequestModal
          items={cart.items.map((line) => ({ productId: line.productId, quantity: line.quantity, name: line.name }))}
          onClose={() => setQuoteModalOpen(false)}
        />
      )}
    </div>
  );
}

/**
 * Turns the current cart into an RFQ — the bulk-order path for a buyer who wants a
 * negotiated price rather than the standard checkout. Reuses whatever is already in the
 * cart rather than a separate product picker, since that is exactly what the shopper
 * was already building.
 */
function QuoteRequestModal({
  items,
  onClose,
}: {
  items: { productId: number; quantity: number; name: string }[];
  onClose: () => void;
}) {
  const { customer } = useCustomerAuth();
  const toast = useToast();
  const [contactName, setContactName] = useState(
    customer ? `${customer.firstName} ${customer.lastName}` : '',
  );
  const [contactEmail, setContactEmail] = useState(customer?.email ?? '');
  const [contactPhone, setContactPhone] = useState(customer?.phone ?? '');
  const [note, setNote] = useState('');
  const [sent, setSent] = useState(false);

  const submit = useMutation({
    mutationFn: () =>
      postData('/customer/quote-requests', {
        contactName,
        contactEmail,
        contactPhone: contactPhone || undefined,
        note: note || undefined,
        items: items.map(({ productId, quantity }) => ({ productId, quantity })),
      }),
    onSuccess: () => setSent(true),
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="w-full max-w-md rounded-[var(--radius-card)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-popover)]"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Təklif sorğusu"
      >
        {sent ? (
          <div className="py-4 text-center">
            <FileText size={28} className="mx-auto mb-3 text-[var(--color-success)]" aria-hidden="true" />
            <h2 className="text-lg font-bold text-[var(--color-heading)]">Sorğunuz göndərildi</h2>
            <p className="mt-1.5 text-sm text-[var(--color-body)]">
              Komandamız qısa zamanda sizə fərdi qiymət təklifi hazırlayacaq. Sorğunuzu hesabınızda
              "Təklif sorğuları" bölməsindən izləyə bilərsiniz.
            </p>
            <button
              type="button"
              onClick={onClose}
              className="mt-5 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)]"
            >
              Bağla
            </button>
          </div>
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              submit.mutate();
            }}
          >
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-[var(--color-heading)]">Təklif sorğusu</h2>
                <p className="mt-1 text-sm text-[var(--color-body)]">
                  Səbətinizdəki {items.length} məhsul üçün topdan qiymət təklifi istəyin.
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Bağla"
                className="shrink-0 rounded p-1 text-[var(--color-subtle)] hover:bg-[var(--color-muted)]"
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <ul className="mb-4 max-h-32 space-y-1 overflow-y-auto rounded-[var(--radius-button)] bg-[var(--color-muted)] p-3 text-xs text-[var(--color-body)]">
              {items.map((item) => (
                <li key={item.productId}>
                  {item.quantity} × {item.name}
                </li>
              ))}
            </ul>

            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">Ad Soyad</label>
                <input
                  required
                  value={contactName}
                  onChange={(event) => setContactName(event.target.value)}
                  className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">E-poçt</label>
                <input
                  required
                  type="email"
                  value={contactEmail}
                  onChange={(event) => setContactEmail(event.target.value)}
                  className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">Telefon</label>
                <input
                  value={contactPhone}
                  onChange={(event) => setContactPhone(event.target.value)}
                  className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">Qeyd</label>
                <textarea
                  rows={3}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="Çatdırılma müddəti, ödəniş şərtləri və s."
                  className="w-full resize-none rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={submit.isPending}
              className="mt-5 w-full rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] disabled:opacity-50"
            >
              {submit.isPending ? 'Göndərilir…' : 'Sorğunu göndər'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: 'success';
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-[var(--color-body)]">{label}</dt>
      <dd
        className={clsx(
          'font-medium tabular',
          tone === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--color-heading)]',
        )}
      >
        {value}
      </dd>
    </div>
  );
}

function NoticeBanner({ notice }: { notice: CartNotice }) {
  return (
    <div
      className={clsx(
        'flex items-start gap-2.5 rounded-[var(--radius-card)] border px-4 py-3 text-sm',
        NOTICE_TONE[notice.type] ?? 'border-[var(--color-border)] bg-[var(--color-muted)]',
      )}
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>
        {notice.productName && <strong>{notice.productName}: </strong>}
        {notice.message}
      </span>
    </div>
  );
}
