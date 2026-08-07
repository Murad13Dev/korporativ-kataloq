import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ShoppingBag } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  type BadgeTone,
} from '../components/ui';
import { getPage } from '@/lib/api';
import { formatDateTime, formatNumber, formatPrice } from '@/lib/format';
import type { OrderSummary } from '@/types/api';

const ORDER_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral',
  AWAITING_PAYMENT: 'warning',
  CONFIRMED: 'info',
  PROCESSING: 'info',
  SHIPPED: 'info',
  DELIVERED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'danger',
  REFUNDED: 'danger',
  EXPIRED: 'neutral',
};

const PAYMENT_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral',
  AUTHORIZED: 'info',
  PAID: 'success',
  PARTIALLY_REFUNDED: 'warning',
  REFUNDED: 'danger',
  FAILED: 'danger',
  CANCELLED: 'neutral',
};

const PAYMENT_LABEL: Record<string, string> = {
  PENDING: 'Gözləyir',
  AUTHORIZED: 'Bloklanıb',
  PAID: 'Ödənilib',
  PARTIALLY_REFUNDED: 'Qismən qaytarılıb',
  REFUNDED: 'Qaytarılıb',
  FAILED: 'Uğursuz',
  CANCELLED: 'Ləğv edilib',
};

const STATUS_OPTIONS = [
  ['', 'Bütün statuslar'],
  ['PENDING', 'Gözləyir'],
  ['AWAITING_PAYMENT', 'Ödəniş gözlənilir'],
  ['CONFIRMED', 'Təsdiqləndi'],
  ['PROCESSING', 'Hazırlanır'],
  ['SHIPPED', 'Yolda'],
  ['DELIVERED', 'Çatdırıldı'],
  ['COMPLETED', 'Tamamlandı'],
  ['CANCELLED', 'Ləğv edildi'],
] as const;

export function OrdersPage() {
  // The dashboard alerts link straight here with a preset filter, so the initial
  // value comes from the URL rather than from local state.
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const status = searchParams.get('status') ?? '';
  const paymentStatus = searchParams.get('paymentStatus') ?? '';

  const orders = useQuery({
    queryKey: ['admin', 'orders', { search, status, paymentStatus, page }],
    queryFn: () =>
      getPage<OrderSummary>('/admin/orders', {
        search: search || undefined,
        status: status || undefined,
        paymentStatus: paymentStatus || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
    // Orders arrive while the operator is looking at the list.
    refetchInterval: 60_000,
  });

  const updateFilter = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setSearchParams(next, { replace: true });
    setPage(0);
  };

  const rows = orders.data;

  return (
    <>
      <PageHeader title="Sifarişlər" description="Bütün sifarişlər və ödəniş statusları" />

      <Card bodyClassName="p-0">
        <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Sifariş nömrəsi, ad, e-poçt"
            className="max-w-xs"
          />
          <Select
            value={status}
            onChange={(event) => updateFilter('status', event.target.value)}
            className="max-w-[200px]"
          >
            {STATUS_OPTIONS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <Select
            value={paymentStatus}
            onChange={(event) => updateFilter('paymentStatus', event.target.value)}
            className="max-w-[180px]"
          >
            <option value="">Bütün ödənişlər</option>
            <option value="PAID">Ödənilib</option>
            <option value="PENDING">Gözləyir</option>
            <option value="FAILED">Uğursuz</option>
            <option value="REFUNDED">Qaytarılıb</option>
          </Select>
        </div>

        {orders.isLoading ? (
          <TableSkeleton rows={8} columns={6} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState
            icon={<ShoppingBag size={36} aria-hidden="true" />}
            title="Sifariş tapılmadı"
            description="Seçilmiş filtrlərə uyğun sifariş yoxdur."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                  <th className="px-5 py-3 text-start font-semibold">Sifariş</th>
                  <th className="px-5 py-3 text-start font-semibold">Müştəri</th>
                  <th className="px-5 py-3 text-start font-semibold">Status</th>
                  <th className="px-5 py-3 text-start font-semibold">Ödəniş</th>
                  <th className="px-5 py-3 text-end font-semibold">Məbləğ</th>
                  <th className="px-5 py-3 text-start font-semibold">Tarix</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {rows.content.map((order) => (
                  <tr key={order.id} className="transition-colors hover:bg-[var(--color-muted)]">
                    <td className="px-5 py-3">
                      <Link
                        to={`/admin/orders/${order.id}`}
                        className="font-medium tabular text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                      >
                        {order.orderNumber}
                      </Link>
                      <p className="text-xs text-[var(--color-subtle)]">
                        {order.itemCount} məhsul
                      </p>
                    </td>
                    <td className="px-5 py-3">
                      <p className="text-[var(--color-heading)]">{order.customerName}</p>
                      <p className="text-xs text-[var(--color-subtle)]">{order.customerEmail}</p>
                    </td>
                    <td className="px-5 py-3">
                      <Badge tone={ORDER_STATUS_TONE[order.status] ?? 'neutral'}>
                        {order.statusLabel}
                      </Badge>
                    </td>
                    <td className="px-5 py-3">
                      <Badge tone={PAYMENT_TONE[order.paymentStatus] ?? 'neutral'}>
                        {PAYMENT_LABEL[order.paymentStatus] ?? order.paymentStatus}
                      </Badge>
                    </td>
                    <td className="px-5 py-3 text-end font-semibold tabular text-[var(--color-heading)]">
                      {formatPrice(order.total)}
                    </td>
                    <td className="px-5 py-3 text-xs text-[var(--color-body)]">
                      {formatDateTime(order.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {rows && rows.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
            <span className="text-xs text-[var(--color-subtle)]">
              {formatNumber(rows.totalElements)} sifariş
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={rows.first}
                onClick={() => setPage((current) => current - 1)}
              >
                Əvvəlki
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={rows.last}
                onClick={() => setPage((current) => current + 1)}
              >
                Növbəti
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
