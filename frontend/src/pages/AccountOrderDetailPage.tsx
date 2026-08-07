import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, PackageX } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, postData } from '@/lib/api';
import { formatDateTime, formatPrice } from '@/lib/format';
import type { OrderDetail } from '@/types/api';

export default function AccountOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const queryKey = ['customer', 'order', id] as const;

  const { data: order, isLoading } = useQuery({
    queryKey,
    queryFn: () => getData<OrderDetail>(`/customer/orders/${id}`),
    enabled: Boolean(id),
  });

  const cancelMutation = useMutation({
    mutationFn: (reason: string) =>
      postData<OrderDetail>(`/customer/orders/${id}/cancel`, { reason }),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKey, updated);
      queryClient.invalidateQueries({ queryKey: ['customer', 'orders'] });
      toast.success('Sifariş ləğv edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (isLoading) {
    return <SectionLoader label="Sifariş yüklənir…" />;
  }

  if (!order) {
    return (
      <div className="card p-8 text-center text-[var(--color-subtle)]">
        Sifariş tapılmadı
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => navigate('/hesab/sifarisler')}
          className="flex items-center gap-1.5 text-sm font-medium text-[var(--color-body)] transition-colors hover:text-[var(--color-primary)]"
        >
          <ArrowLeft size={16} aria-hidden="true" />
          Sifarişlərə qayıt
        </button>

        {order.cancellable && (
          <button
            type="button"
            onClick={() => {
              if (window.confirm('Sifarişi ləğv etmək istədiyinizə əminsiniz?')) {
                cancelMutation.mutate('Müştəri tərəfindən ləğv edildi');
              }
            }}
            disabled={cancelMutation.isPending}
            className="flex items-center gap-1.5 rounded-[var(--radius-button)] border border-[var(--color-danger)] px-4 py-2 text-sm font-medium text-[var(--color-danger)] transition-colors hover:bg-[var(--color-danger)]/10 disabled:opacity-50"
          >
            <PackageX size={16} aria-hidden="true" />
            Sifarişi ləğv et
          </button>
        )}
      </div>

      <div className="card p-5 sm:p-6">
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3 border-b border-[var(--color-border)] pb-5">
          <div>
            <h1 className="text-xl font-bold">Sifariş #{order.orderNumber}</h1>
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
                {item.productSlug ? (
                  <Link
                    to={`/mehsul/${item.productSlug}`}
                    className="line-clamp-1 text-sm font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                  >
                    {item.productName}
                  </Link>
                ) : (
                  <span className="line-clamp-1 text-sm font-medium text-[var(--color-heading)]">
                    {item.productName}
                  </span>
                )}
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

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="card p-5">
          <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
            Çatdırılma ünvanı
          </h2>
          <p className="text-sm text-[var(--color-body)]">{order.customerName}</p>
          {order.customerPhone && <p className="text-sm text-[var(--color-body)]">{order.customerPhone}</p>}
          <p className="mt-1 text-sm text-[var(--color-body)]">
            {[order.shippingAddressLine, order.shippingDistrict, order.shippingCity, order.shippingCountry]
              .filter(Boolean)
              .join(', ') || 'Mağazadan götürmə'}
          </p>
        </div>

        <div className="card p-5">
          <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
            Ödəniş
          </h2>
          <p className="text-sm text-[var(--color-body)]">
            {order.paymentMethod === 'ONLINE'
              ? 'Onlayn kart ödənişi'
              : order.paymentMethod === 'CASH_ON_DELIVERY'
                ? 'Çatdırılma zamanı nağd'
                : 'Bank köçürməsi'}
          </p>
          <p className="mt-1 text-sm text-[var(--color-subtle)]">{order.paymentStatus}</p>
        </div>
      </div>

      {order.history.length > 0 && (
        <div className="card p-5">
          <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
            Status tarixçəsi
          </h2>
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
  );
}
