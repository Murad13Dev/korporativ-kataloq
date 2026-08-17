import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { ChevronLeft, ChevronRight, Package, RotateCcw } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useReorder } from '@/hooks/useCart';
import { getPage } from '@/lib/api';
import { formatDate, formatPrice } from '@/lib/format';
import type { OrderStatus, OrderSummary } from '@/types/api';

const PAGE_SIZE = 10;

const STATUS_STYLE: Record<OrderStatus, string> = {
  PENDING: 'bg-[var(--color-muted)] text-[var(--color-subtle)]',
  AWAITING_PAYMENT: 'bg-[var(--color-warning)]/10 text-[var(--color-warning)]',
  CONFIRMED: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  PROCESSING: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  SHIPPED: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
  DELIVERED: 'bg-[var(--color-success)]/10 text-[var(--color-success)]',
  COMPLETED: 'bg-[var(--color-success)]/10 text-[var(--color-success)]',
  CANCELLED: 'bg-[var(--color-danger)]/10 text-[var(--color-danger)]',
  REFUNDED: 'bg-[var(--color-danger)]/10 text-[var(--color-danger)]',
  EXPIRED: 'bg-[var(--color-muted)] text-[var(--color-subtle)]',
};

export default function AccountOrdersPage() {
  const [page, setPage] = useState(0);
  const reorder = useReorder();

  const { data, isLoading } = useQuery({
    queryKey: ['customer', 'orders', page],
    queryFn: () =>
      getPage<OrderSummary>('/customer/orders', { page, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  });

  if (isLoading) {
    return <SectionLoader label="Sifarişlər yüklənir…" />;
  }

  const orders = data?.content ?? [];

  if (orders.length === 0 && page === 0) {
    return (
      <div className="card flex flex-col items-center gap-2 p-12 text-center text-[var(--color-subtle)]">
        <Package size={28} aria-hidden="true" />
        <p className="text-sm">Hələ sifarişiniz yoxdur</p>
        <Link to="/kataloq" className="mt-2 text-sm font-medium text-[var(--color-primary)] hover:underline">
          Kataloqa keç
        </Link>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <ul className="divide-y divide-[var(--color-border)]">
        {orders.map((order) => (
          <li key={order.id} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
            <Link
              to={`/hesab/sifarisler/${order.id}`}
              className="min-w-0 flex-1 transition-colors hover:text-[var(--color-primary)]"
            >
              <div className="flex items-center gap-2">
                <span className="font-semibold text-[var(--color-heading)]">#{order.orderNumber}</span>
                <span
                  className={clsx(
                    'rounded-full px-2 py-0.5 text-[0.6875rem] font-semibold',
                    STATUS_STYLE[order.status],
                  )}
                >
                  {order.statusLabel}
                </span>
              </div>
              <p className="mt-1 text-xs text-[var(--color-subtle)]">
                {formatDate(order.createdAt)} · {order.itemCount} məhsul
              </p>
            </Link>

            <div className="flex shrink-0 items-center gap-3">
              <span className="font-display text-lg font-bold tabular">{formatPrice(order.total)}</span>
              <button
                type="button"
                onClick={() => reorder.mutate(order.id)}
                disabled={reorder.isPending}
                aria-label="Yenidən sifariş et"
                title="Yenidən sifariş et"
                className="flex items-center gap-1.5 rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-2 text-sm font-medium text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] disabled:opacity-50"
              >
                <RotateCcw size={14} aria-hidden="true" />
                <span className="hidden sm:inline">Yenidən sifariş</span>
              </button>
            </div>
          </li>
        ))}
      </ul>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-[var(--color-border)] px-4 py-3">
          <button
            type="button"
            disabled={data.first}
            onClick={() => setPage((current) => current - 1)}
            className="flex items-center gap-1 rounded-[var(--radius-button)] px-3 py-1.5 text-sm font-medium text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] disabled:opacity-40"
          >
            <ChevronLeft size={16} aria-hidden="true" />
            Əvvəlki
          </button>
          <span className="text-xs text-[var(--color-subtle)]">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={data.last}
            onClick={() => setPage((current) => current + 1)}
            className="flex items-center gap-1 rounded-[var(--radius-button)] px-3 py-1.5 text-sm font-medium text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] disabled:opacity-40"
          >
            Növbəti
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}
