import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, RotateCcw, Save } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  Field,
  InlineLoader,
  PageHeader,
  Select,
  Textarea,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { useCan } from '../auth/adminAuth';
import { getData, patchData, postData, putData } from '@/lib/api';
import { formatDateTime, formatPrice } from '@/lib/format';
import type { OrderDetail, OrderStatus } from '@/types/api';

const STATUS_TONE: Record<string, BadgeTone> = {
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

/**
 * Only transitions the server's state machine actually allows are offered. Presenting
 * every status and letting the API reject the choice would train operators to expect
 * failures; the dropdown mirrors `OrderStatus.allowedNext()`.
 */
const ALLOWED_NEXT: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['AWAITING_PAYMENT', 'CONFIRMED', 'CANCELLED'],
  AWAITING_PAYMENT: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['PROCESSING', 'SHIPPED', 'CANCELLED', 'REFUNDED'],
  PROCESSING: ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED: ['DELIVERED', 'REFUNDED'],
  DELIVERED: ['COMPLETED', 'REFUNDED'],
  COMPLETED: ['REFUNDED'],
  CANCELLED: [],
  REFUNDED: [],
  EXPIRED: [],
};

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Gözləyir',
  AWAITING_PAYMENT: 'Ödəniş gözlənilir',
  CONFIRMED: 'Təsdiqləndi',
  PROCESSING: 'Hazırlanır',
  SHIPPED: 'Yolda',
  DELIVERED: 'Çatdırıldı',
  COMPLETED: 'Tamamlandı',
  CANCELLED: 'Ləğv edildi',
  REFUNDED: 'Geri qaytarıldı',
  EXPIRED: 'Vaxtı bitdi',
};

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const canRefund = useCan('orders.refund');

  const [nextStatus, setNextStatus] = useState<string>('');
  const [statusNote, setStatusNote] = useState('');
  const [adminNote, setAdminNote] = useState<string | null>(null);

  const order = useQuery({
    queryKey: ['admin', 'order', id],
    queryFn: () => getData<OrderDetail>(`/admin/orders/${id}`),
    enabled: Boolean(id),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'order', id] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
  };

  const changeStatus = useMutation({
    mutationFn: () =>
      patchData(`/admin/orders/${id}/status`, { status: nextStatus, note: statusNote || null }),
    onSuccess: () => {
      invalidate();
      setNextStatus('');
      setStatusNote('');
      toast.success('Status yeniləndi');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const saveNote = useMutation({
    mutationFn: () => putData(`/admin/orders/${id}/note`, { note: adminNote }),
    onSuccess: () => {
      invalidate();
      toast.success('Qeyd yadda saxlanıldı');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const refund = useMutation({
    mutationFn: (reason: string) => postData(`/admin/orders/${id}/refund`, { reason }),
    onSuccess: () => {
      invalidate();
      toast.success('Məbləğ geri qaytarıldı');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  if (order.isLoading || !order.data) {
    return <InlineLoader label="Sifariş yüklənir…" />;
  }

  const data = order.data;
  const allowedTransitions = ALLOWED_NEXT[data.status] ?? [];

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link
            to="/admin/orders"
            className="inline-flex items-center gap-1.5 hover:text-[var(--color-heading)]"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            Sifarişlər
          </Link>
        }
        title={data.orderNumber}
        description={`${formatDateTime(data.createdAt)} · ${data.customerName}`}
        actions={<Badge tone={STATUS_TONE[data.status] ?? 'neutral'}>{data.statusLabel}</Badge>}
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          {/* ---------------- items ---------------- */}
          <Card title="Məhsullar" bodyClassName="p-0">
            <ul className="divide-y divide-[var(--color-border)]">
              {data.items.map((item) => (
                <li key={item.id} className="flex items-center gap-4 px-5 py-3.5">
                  <div className="h-12 w-12 shrink-0 overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
                    {item.imageUrl && (
                      <img src={item.imageUrl} alt="" className="h-full w-full object-cover" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-[var(--color-heading)]">
                      {item.productName}
                      {item.variantLabel && (
                        <span className="ms-1.5 font-normal text-[var(--color-subtle)]">
                          ({item.variantLabel})
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-[var(--color-subtle)]">
                      {item.variantSku ?? item.productSku} · {item.quantity} ×{' '}
                      {formatPrice(item.unitPrice)}
                      {item.discounted && (
                        <span className="ms-1 line-through">
                          {formatPrice(item.originalUnitPrice)}
                        </span>
                      )}
                    </p>
                    {item.discountName && (
                      <Badge tone="sale" className="mt-1">
                        {item.discountName}
                      </Badge>
                    )}
                  </div>
                  <span className="font-semibold tabular text-[var(--color-heading)]">
                    {formatPrice(item.totalPrice)}
                  </span>
                </li>
              ))}
            </ul>

            <dl className="space-y-2 border-t border-[var(--color-border)] px-5 py-4 text-sm">
              <Row label="Ara cəm" value={formatPrice(data.subtotal)} />
              {data.discountTotal > 0 && (
                <Row
                  label="Kampaniya endirimi"
                  value={`− ${formatPrice(data.discountTotal)}`}
                  tone="success"
                />
              )}
              {data.promoDiscount > 0 && (
                <Row
                  label={`Promo kod (${data.promoCode})`}
                  value={`− ${formatPrice(data.promoDiscount)}`}
                  tone="success"
                />
              )}
              <Row label="Çatdırılma" value={formatPrice(data.deliveryPrice)} />
              <div className="flex items-baseline justify-between border-t border-[var(--color-border)] pt-2.5">
                <dt className="font-semibold text-[var(--color-heading)]">Yekun</dt>
                <dd className="font-display text-xl font-bold tabular text-[var(--color-heading)]">
                  {formatPrice(data.total)}
                </dd>
              </div>
            </dl>
          </Card>

          {/* ---------------- history ---------------- */}
          <Card title="Status tarixçəsi" bodyClassName="p-0">
            <ol className="divide-y divide-[var(--color-border)]">
              {data.history.map((entry, index) => (
                <li key={index} className="flex items-start gap-3 px-5 py-3">
                  <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-[var(--color-heading)]">
                      {entry.toStatusLabel}
                    </p>
                    {entry.note && (
                      <p className="text-xs text-[var(--color-body)]">{entry.note}</p>
                    )}
                    <p className="text-xs text-[var(--color-subtle)]">
                      {formatDateTime(entry.createdAt)} · {entry.actorType}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </div>

        {/* ---------------- side ---------------- */}
        <div className="space-y-6">
          <Card title="Status dəyişmək">
            {allowedTransitions.length === 0 ? (
              <p className="text-sm text-[var(--color-subtle)]">
                Bu sifariş yekun vəziyyətdədir — status dəyişdirilə bilməz.
              </p>
            ) : (
              <div className="space-y-3">
                <Field label="Yeni status" htmlFor="nextStatus">
                  <Select
                    id="nextStatus"
                    value={nextStatus}
                    onChange={(event) => setNextStatus(event.target.value)}
                  >
                    <option value="">Seçin…</option>
                    {allowedTransitions.map((status) => (
                      <option key={status} value={status}>
                        {STATUS_LABEL[status]}
                      </option>
                    ))}
                  </Select>
                </Field>

                <Field label="Qeyd" htmlFor="statusNote">
                  <Textarea
                    id="statusNote"
                    rows={2}
                    value={statusNote}
                    onChange={(event) => setStatusNote(event.target.value)}
                    placeholder="İstəyə bağlı"
                  />
                </Field>

                <Button
                  disabled={!nextStatus}
                  loading={changeStatus.isPending}
                  onClick={() => changeStatus.mutate()}
                  className="w-full"
                >
                  Statusu dəyiş
                </Button>
              </div>
            )}
          </Card>

          <Card title="Müştəri">
            <dl className="space-y-2 text-sm">
              <Row label="Ad" value={data.customerName} />
              <Row label="E-poçt" value={data.customerEmail} />
              {data.customerPhone && <Row label="Telefon" value={data.customerPhone} />}
              {data.companyName && <Row label="Şirkət" value={data.companyName} />}
            </dl>

            {data.shippingAddressLine && (
              <div className="mt-4 border-t border-[var(--color-border)] pt-4">
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                  Çatdırılma ünvanı
                </p>
                <p className="text-sm text-[var(--color-body)]">
                  {[data.shippingCity, data.shippingDistrict, data.shippingAddressLine]
                    .filter(Boolean)
                    .join(', ')}
                </p>
              </div>
            )}

            {data.customerNote && (
              <div className="mt-4 border-t border-[var(--color-border)] pt-4">
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                  Müştəri qeydi
                </p>
                <p className="text-sm text-[var(--color-body)]">{data.customerNote}</p>
              </div>
            )}
          </Card>

          <Card title="Daxili qeyd" description="Yalnız admin panelində görünür">
            <Textarea
              rows={4}
              value={adminNote ?? data.adminNote ?? ''}
              onChange={(event) => setAdminNote(event.target.value)}
            />
            <Button
              variant="secondary"
              size="sm"
              className="mt-3 w-full"
              loading={saveNote.isPending}
              icon={<Save size={14} aria-hidden="true" />}
              onClick={() => saveNote.mutate()}
            >
              Yadda saxla
            </Button>
          </Card>

          {canRefund && data.paymentStatus === 'PAID' && (
            <Card title="Geri qaytarma">
              <p className="mb-3 text-sm text-[var(--color-body)]">
                Tam məbləğ ({formatPrice(data.total)}) müştəriyə geri qaytarılacaq.
              </p>
              <Button
                variant="danger"
                className="w-full"
                loading={refund.isPending}
                icon={<RotateCcw size={14} aria-hidden="true" />}
                onClick={() => {
                  const reason = window.prompt('Geri qaytarma səbəbi:');
                  if (reason !== null) {
                    refund.mutate(reason);
                  }
                }}
              >
                Məbləği geri qaytar
              </Button>
            </Card>
          )}
        </div>
      </div>
    </>
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
      <dt className="shrink-0 text-[var(--color-body)]">{label}</dt>
      <dd
        className={
          tone === 'success'
            ? 'text-end font-medium tabular text-[var(--color-success)]'
            : 'text-end font-medium tabular text-[var(--color-heading)]'
        }
      >
        {value}
      </dd>
    </div>
  );
}
