import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { PackageSearch, Search } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { ApiError, postData } from '@/lib/api';
import { formatDateTime, formatPrice } from '@/lib/format';
import type { OrderDetail } from '@/types/api';

/**
 * Guest order lookup: number + e-mail, no account needed.
 *
 * Mirrors `AccountOrderDetailPage`'s result layout closely enough to feel like the same
 * product, but stays a separate component — a guest here never sees a cancel action or
 * a "back to my orders" link, so folding the two together would mean threading that
 * distinction through every section instead of just not rendering it here.
 */
export default function OrderTrackingPage() {
  const [orderNumber, setOrderNumber] = useState('');
  const [email, setEmail] = useState('');

  const trackMutation = useMutation({
    mutationFn: () => postData<OrderDetail>('/public/checkout/track', { orderNumber, email }),
  });

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    trackMutation.mutate();
  };

  const order = trackMutation.data;
  const notFound = trackMutation.isError && trackMutation.error instanceof ApiError
    && trackMutation.error.is('RESOURCE_NOT_FOUND');

  return (
    <div className="container-site max-w-2xl py-12 lg:py-16">
      <div className="mb-8 text-center">
        <PackageSearch size={32} className="mx-auto mb-3 text-[var(--color-primary)]" aria-hidden="true" />
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Sifarişi izlə</h1>
        <p className="mt-2 text-sm text-[var(--color-subtle)]">
          Hesabınız yoxdursa belə, sifariş nömrənizi və e-poçtunuzu daxil edərək statusunu görə bilərsiniz.
        </p>
      </div>

      <form onSubmit={onSubmit} className="card flex flex-col gap-4 p-5 sm:flex-row sm:items-end sm:p-6">
        <Field label="Sifariş nömrəsi" className="flex-1">
          <input
            value={orderNumber}
            onChange={(event) => setOrderNumber(event.target.value)}
            placeholder="ORD-20260101-0001"
            required
            className={inputClass(false)}
          />
        </Field>
        <Field label="E-poçt" className="flex-1">
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="siz@example.com"
            required
            className={inputClass(false)}
          />
        </Field>
        <button
          type="submit"
          disabled={trackMutation.isPending}
          className="flex items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
        >
          <Search size={16} aria-hidden="true" />
          Axtar
        </button>
      </form>

      {notFound && (
        <p className="mt-4 rounded-[var(--radius-card)] border border-[var(--color-danger)]/30 bg-[var(--color-danger)]/5 px-4 py-3 text-center text-sm text-[var(--color-danger)]">
          Bu nömrə və e-poçt ilə sifariş tapılmadı. Məlumatları yoxlayıb yenidən cəhd edin.
        </p>
      )}

      {order && (
        <div className="mt-8 space-y-5">
          <div className="card p-5 sm:p-6">
            <div className="mb-5 flex flex-wrap items-start justify-between gap-3 border-b border-[var(--color-border)] pb-5">
              <div>
                <h2 className="text-xl font-bold">Sifariş #{order.orderNumber}</h2>
                <p className="mt-1 text-sm text-[var(--color-subtle)]">
                  {formatDateTime(order.placedAt ?? order.createdAt)}
                </p>
              </div>
              <span className="rounded-full bg-[var(--color-primary)]/10 px-3 py-1 text-sm font-semibold text-[var(--color-primary)]">
                {order.statusLabel}
              </span>
            </div>

            <ul className="mb-5 space-y-3">
              {order.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3">
                  <div className="h-14 w-14 shrink-0 overflow-hidden rounded-[var(--radius-input)] bg-[var(--color-muted)]">
                    {item.imageUrl && (
                      <img src={item.imageUrl} alt="" className="h-full w-full object-cover" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <span className="line-clamp-1 text-sm font-medium text-[var(--color-heading)]">
                      {item.productName}
                    </span>
                    {item.variantLabel && (
                      <span className="block text-xs text-[var(--color-body)]">{item.variantLabel}</span>
                    )}
                    <span className="text-xs text-[var(--color-subtle)] tabular">
                      {item.quantity} × {formatPrice(item.unitPrice)}
                    </span>
                  </div>
                  <span className="shrink-0 font-semibold tabular">{formatPrice(item.totalPrice)}</span>
                </li>
              ))}
            </ul>

            <dl className="space-y-2 border-t border-[var(--color-border)] pt-4 text-sm">
              <div className="flex justify-between">
                <dt className="text-[var(--color-body)]">Ara cəm</dt>
                <dd className="tabular">{formatPrice(order.subtotal)}</dd>
              </div>
              {order.totalSaving > 0 && (
                <div className="flex justify-between text-[var(--color-success)]">
                  <dt>Endirim</dt>
                  <dd className="tabular">− {formatPrice(order.totalSaving)}</dd>
                </div>
              )}
              <div className="flex justify-between">
                <dt className="text-[var(--color-body)]">Çatdırılma</dt>
                <dd className="tabular">{order.deliveryPrice > 0 ? formatPrice(order.deliveryPrice) : 'Pulsuz'}</dd>
              </div>
              <div className="flex justify-between border-t border-[var(--color-border)] pt-2 text-base font-bold">
                <dt>Yekun</dt>
                <dd className="tabular">{formatPrice(order.total)}</dd>
              </div>
            </dl>
          </div>

          <div className="card p-5">
            <h3 className="mb-3 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
              Çatdırılma ünvanı
            </h3>
            <p className="text-sm text-[var(--color-body)]">{order.customerName}</p>
            {order.customerPhone && <p className="text-sm text-[var(--color-body)]">{order.customerPhone}</p>}
            <p className="mt-1 text-sm text-[var(--color-body)]">
              {[order.shippingAddressLine, order.shippingDistrict, order.shippingCity, order.shippingCountry]
                .filter(Boolean)
                .join(', ') || 'Mağazadan götürmə'}
            </p>
          </div>

          {order.history.length > 0 && (
            <div className="card p-5">
              <h3 className="mb-4 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
                Status tarixçəsi
              </h3>
              <ol className="space-y-3">
                {order.history.map((entry, index) => (
                  <li key={index} className="flex items-start gap-3 text-sm">
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                    <div>
                      <span className="font-medium text-[var(--color-heading)]">{entry.toStatusLabel}</span>
                      <span className="ms-2 text-xs text-[var(--color-subtle)] tabular">
                        {formatDateTime(entry.createdAt)}
                      </span>
                      {entry.note && <p className="text-xs text-[var(--color-subtle)]">{entry.note}</p>}
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>
      )}

      <p className="mt-8 text-center text-sm text-[var(--color-body)]">
        Hesabınız var?{' '}
        <Link to="/hesab/sifarisler" className="font-medium text-[var(--color-primary)] hover:underline">
          Bütün sifarişlərinizi görün
        </Link>
      </p>
    </div>
  );
}
