import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban, CheckCircle2, MapPin, ShoppingBag, UserRound, UsersRound } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  InlineLoader,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { useCan } from '../auth/adminAuth';
import { ApiError, getData, getPage, patchData } from '@/lib/api';
import { formatDate, formatDateTime, formatNumber, formatPrice } from '@/lib/format';
import type { Address, CustomerProfile, OrderSummary } from '@/types/api';

type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED' | 'GUEST';

interface CustomerRow {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  companyName: string | null;
  status: CustomerStatus;
  orderCount: number;
  lastLoginAt: string | null;
  createdAt: string;
}

interface CustomerDetail {
  profile: CustomerProfile;
  status: CustomerStatus;
  guest: boolean;
  addresses: Address[];
  recentOrders: OrderSummary[];
  stats: {
    orderCount: number;
    lifetimeValue: number;
    lastOrderAt: string | null;
  };
}

const STATUS_TONE: Record<CustomerStatus, BadgeTone> = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  BLOCKED: 'danger',
  GUEST: 'warning',
};

const STATUS_LABEL: Record<CustomerStatus, string> = {
  ACTIVE: 'Aktiv',
  INACTIVE: 'Deaktiv',
  BLOCKED: 'Bloklanıb',
  GUEST: 'Qonaq',
};

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

/**
 * Customer administration.
 *
 * Read-mostly by design: a customer's name, address and password are theirs, and an
 * operator quietly editing them would break the trail on orders already placed. What an
 * operator genuinely needs is history and the ability to block an account — that is the
 * whole surface here.
 */
export function CustomersPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const canDelete = useCan('customers.view');

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const customers = useQuery({
    queryKey: ['admin', 'customers', { search, status, page }],
    queryFn: () =>
      getPage<CustomerRow>('/admin/customers', {
        search: search || undefined,
        status: status || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
  });

  const detail = useQuery({
    queryKey: ['admin', 'customer', selectedId],
    queryFn: () => getData<CustomerDetail>(`/admin/customers/${selectedId}`),
    enabled: selectedId !== null,
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: number; next: CustomerStatus }) =>
      patchData<CustomerDetail>(`/admin/customers/${id}/status`, { status: next }),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'customer', variables.id] });
      toast.success(
        variables.next === 'BLOCKED'
          ? 'Müştəri bloklandı və bütün sessiyaları bağlandı'
          : 'Status dəyişdirildi',
      );
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const rows = customers.data;

  return (
    <>
      <PageHeader
        title="Müştərilər"
        description="Saytda qeydiyyatdan keçmiş və qonaq kimi sifariş vermiş şəxslər"
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Card bodyClassName="p-0">
          <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Ad, e-poçt və ya telefon"
              className="max-w-xs"
            />
            <Select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
              className="max-w-[170px]"
            >
              <option value="">Bütün statuslar</option>
              <option value="ACTIVE">Aktiv</option>
              <option value="GUEST">Qonaq</option>
              <option value="INACTIVE">Deaktiv</option>
              <option value="BLOCKED">Bloklanıb</option>
            </Select>
          </div>

          {customers.isLoading ? (
            <TableSkeleton rows={8} columns={4} />
          ) : !rows || rows.content.length === 0 ? (
            <EmptyState
              icon={<UsersRound size={36} aria-hidden="true" />}
              title="Müştəri tapılmadı"
              description="Seçilmiş filtrlərə uyğun müştəri yoxdur."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                    <th className="px-5 py-3 text-start font-semibold">Müştəri</th>
                    <th className="px-5 py-3 text-start font-semibold">Status</th>
                    <th className="px-5 py-3 text-end font-semibold">Sifariş</th>
                    <th className="px-5 py-3 text-start font-semibold">Qeydiyyat</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border)]">
                  {rows.content.map((customer) => (
                    <tr
                      key={customer.id}
                      onClick={() => setSelectedId(customer.id)}
                      className={
                        selectedId === customer.id
                          ? 'cursor-pointer bg-[var(--color-primary)]/6'
                          : 'cursor-pointer transition-colors hover:bg-[var(--color-muted)]'
                      }
                    >
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-3">
                          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--color-muted)] text-xs font-bold text-[var(--color-body)]">
                            {customer.firstName.charAt(0)}
                            {customer.lastName.charAt(0)}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium text-[var(--color-heading)]">
                              {customer.firstName} {customer.lastName}
                            </p>
                            <p className="truncate text-xs text-[var(--color-subtle)]">
                              {customer.email}
                            </p>
                          </div>
                        </div>
                      </td>

                      <td className="px-5 py-3">
                        <Badge tone={STATUS_TONE[customer.status]}>
                          {STATUS_LABEL[customer.status]}
                        </Badge>
                      </td>

                      <td className="px-5 py-3 text-end tabular text-[var(--color-heading)]">
                        {formatNumber(customer.orderCount)}
                      </td>

                      <td className="px-5 py-3 text-xs text-[var(--color-body)]">
                        {formatDate(customer.createdAt)}
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
                {formatNumber(rows.totalElements)} müştəri
              </span>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" disabled={rows.first} onClick={() => setPage((p) => p - 1)}>
                  Əvvəlki
                </Button>
                <Button variant="secondary" size="sm" disabled={rows.last} onClick={() => setPage((p) => p + 1)}>
                  Növbəti
                </Button>
              </div>
            </div>
          )}
        </Card>

        {/* ---------------- detail ---------------- */}
        <div className="lg:sticky lg:top-24 lg:self-start">
          {selectedId === null ? (
            <Card>
              <EmptyState
                icon={<UserRound size={32} aria-hidden="true" />}
                title="Müştəri seçin"
                description="Detalları, ünvanları və sifariş tarixçəsini görmək üçün sətrə klikləyin."
              />
            </Card>
          ) : detail.isLoading || !detail.data ? (
            <Card>
              <InlineLoader />
            </Card>
          ) : (
            <div className="space-y-4">
              <Card
                title={`${detail.data.profile.firstName} ${detail.data.profile.lastName}`}
                actions={
                  <Button variant="ghost" size="sm" onClick={() => setSelectedId(null)}>
                    Bağla
                  </Button>
                }
              >
                <dl className="space-y-2 text-sm">
                  <Row label="E-poçt" value={detail.data.profile.email} />
                  {detail.data.profile.phone && (
                    <Row label="Telefon" value={detail.data.profile.phone} />
                  )}
                  {detail.data.profile.companyName && (
                    <Row label="Şirkət" value={detail.data.profile.companyName} />
                  )}
                  <Row
                    label="Qeydiyyat"
                    value={formatDate(detail.data.profile.createdAt)}
                  />
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-[var(--color-body)]">Status</dt>
                    <dd>
                      <Badge tone={STATUS_TONE[detail.data.status]}>
                        {STATUS_LABEL[detail.data.status]}
                      </Badge>
                    </dd>
                  </div>
                </dl>

                {detail.data.guest && (
                  <p className="mt-3 rounded-[var(--radius-button)] bg-[var(--color-warning)]/10 px-3 py-2 text-xs text-[var(--color-warning)]">
                    Bu qeyd qonaq sifarişindən yaranıb — şifrəsi yoxdur. Müştəri eyni
                    e-poçtla qeydiyyatdan keçsə, sifariş tarixçəsi ilə birlikdə hesaba çevriləcək.
                  </p>
                )}

                {canDelete && detail.data.status !== 'GUEST' && (
                  <div className="mt-4 border-t border-[var(--color-border)] pt-4">
                    {detail.data.status === 'BLOCKED' ? (
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full"
                        loading={changeStatus.isPending}
                        icon={<CheckCircle2 size={14} aria-hidden="true" />}
                        onClick={() =>
                          changeStatus.mutate({ id: detail.data.profile.id, next: 'ACTIVE' })
                        }
                      >
                        Bloku qaldır
                      </Button>
                    ) : (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="w-full text-[var(--color-danger)]"
                        loading={changeStatus.isPending}
                        icon={<Ban size={14} aria-hidden="true" />}
                        onClick={() => {
                          if (
                            window.confirm(
                              'Müştəri bloklanacaq və bütün aktiv sessiyaları dərhal bağlanacaq. Davam edilsin?',
                            )
                          ) {
                            changeStatus.mutate({ id: detail.data.profile.id, next: 'BLOCKED' });
                          }
                        }}
                      >
                        Hesabı blokla
                      </Button>
                    )}
                  </div>
                )}
              </Card>

              <Card title="Göstəricilər">
                <dl className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                      Sifariş
                    </dt>
                    <dd className="mt-0.5 font-display text-xl font-bold tabular text-[var(--color-heading)]">
                      {formatNumber(detail.data.stats.orderCount)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                      Ümumi xərc
                    </dt>
                    <dd className="mt-0.5 font-display text-xl font-bold tabular text-[var(--color-success)]">
                      {formatPrice(detail.data.stats.lifetimeValue)}
                    </dd>
                  </div>
                </dl>
                {detail.data.stats.lastOrderAt && (
                  <p className="mt-3 text-xs text-[var(--color-subtle)]">
                    Son sifariş: {formatDateTime(detail.data.stats.lastOrderAt)}
                  </p>
                )}
              </Card>

              {detail.data.addresses.length > 0 && (
                <Card title="Ünvanlar" bodyClassName="p-0">
                  <ul className="divide-y divide-[var(--color-border)]">
                    {detail.data.addresses.map((address) => (
                      <li key={address.id} className="px-5 py-3">
                        <div className="flex items-start gap-2.5">
                          <MapPin
                            size={15}
                            className="mt-0.5 shrink-0 text-[var(--color-subtle)]"
                            aria-hidden="true"
                          />
                          <div className="min-w-0">
                            <p className="text-sm font-medium text-[var(--color-heading)]">
                              {address.title}
                              {address.isDefault && (
                                <Badge tone="info" className="ms-2">
                                  Əsas
                                </Badge>
                              )}
                            </p>
                            <p className="text-xs text-[var(--color-body)]">
                              {[address.city, address.district, address.addressLine]
                                .filter(Boolean)
                                .join(', ')}
                            </p>
                          </div>
                        </div>
                      </li>
                    ))}
                  </ul>
                </Card>
              )}

              <Card title="Son sifarişlər" bodyClassName="p-0">
                {detail.data.recentOrders.length === 0 ? (
                  <p className="px-5 py-8 text-center text-sm text-[var(--color-subtle)]">
                    Hələ sifariş yoxdur
                  </p>
                ) : (
                  <ul className="divide-y divide-[var(--color-border)]">
                    {detail.data.recentOrders.map((order) => (
                      <li key={order.id}>
                        <Link
                          to={`/admin/orders/${order.id}`}
                          className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-[var(--color-muted)]"
                        >
                          <ShoppingBag
                            size={15}
                            className="shrink-0 text-[var(--color-subtle)]"
                            aria-hidden="true"
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium tabular text-[var(--color-heading)]">
                              {order.orderNumber}
                            </p>
                            <p className="text-xs text-[var(--color-subtle)]">
                              {formatDate(order.createdAt)}
                            </p>
                          </div>
                          <Badge tone={ORDER_STATUS_TONE[order.status] ?? 'neutral'}>
                            {order.statusLabel}
                          </Badge>
                          <span className="shrink-0 text-sm font-semibold tabular text-[var(--color-heading)]">
                            {formatPrice(order.total)}
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="shrink-0 text-[var(--color-body)]">{label}</dt>
      <dd className="truncate text-end font-medium text-[var(--color-heading)]">{value}</dd>
    </div>
  );
}
