import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Pause, Play, Plus, Tag, Trash2 } from 'lucide-react';
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
import { useToast } from '@/components/ui/Toast';
import { deleteRequest, getPage, patchData } from '@/lib/api';
import { formatDate, formatNumber } from '@/lib/format';

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';
type DiscountScope = 'ALL_PRODUCTS' | 'CATEGORIES' | 'PRODUCTS';
type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface DiscountSummary {
  id: number;
  name: string;
  discountType: DiscountType;
  value: number;
  scope: DiscountScope;
  status: Status;
  startsAt: string | null;
  endsAt: string | null;
  priority: number;
  badgeStyle: string;
  badgeLabel: string | null;
  running: boolean;
  scheduled: boolean;
  expired: boolean;
  targetCount: number;
}

const SCOPE_LABELS: Record<DiscountScope, string> = {
  ALL_PRODUCTS: 'Bütün məhsullar',
  CATEGORIES: 'Kateqoriyalar',
  PRODUCTS: 'Seçilmiş məhsullar',
};

/**
 * Campaign list.
 *
 * The "state" column is computed, not stored: a campaign is running, scheduled, expired
 * or paused depending on its status *and* the clock. Showing the raw DRAFT/ACTIVE enum
 * would leave an operator unable to tell a live campaign from one that ended last week.
 */
export function DiscountsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>('');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'discounts', { search, status, page }],
    queryFn: () =>
      getPage<DiscountSummary>('/admin/discounts', {
        search: search || undefined,
        status: status || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'discounts'] });
    // A campaign change repaints every storefront price, so drop those caches too.
    queryClient.invalidateQueries({ queryKey: ['products'] });
  };

  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: number; next: Status }) =>
      patchData(`/admin/discounts/${id}/status`, { status: next }),
    onSuccess: (_, variables) => {
      invalidate();
      toast.success(
        variables.next === 'ACTIVE' ? 'Kampaniya aktivləşdirildi' : 'Kampaniya dayandırıldı',
      );
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/discounts/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Kampaniya silindi');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const discounts = query.data;

  return (
    <>
      <PageHeader
        title="Endirim kampaniyaları"
        description="Planlaşdırılan endirimlər. Kampaniya bitəndə qiymətlər avtomatik bərpa olunur."
        actions={
          <Link to="/admin/discounts/new">
            <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni kampaniya</Button>
          </Link>
        }
      />

      <Card bodyClassName="p-0">
        {/* ---------------- filters ---------------- */}
        <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Kampaniya adı"
            className="max-w-xs"
          />
          <Select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
            className="max-w-[180px]"
          >
            <option value="">Bütün statuslar</option>
            <option value="ACTIVE">Aktiv</option>
            <option value="DRAFT">Qaralama</option>
            <option value="ARCHIVED">Arxiv</option>
          </Select>
        </div>

        {query.isLoading ? (
          <TableSkeleton rows={6} columns={6} />
        ) : !discounts || discounts.content.length === 0 ? (
          <EmptyState
            icon={<Tag size={36} aria-hidden="true" />}
            title="Kampaniya yoxdur"
            description="İlk endirim kampaniyanızı yaradın. Tarix aralığı təyin etsəniz avtomatik başlayıb bitəcək."
            action={
              <Link to="/admin/discounts/new">
                <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni kampaniya</Button>
              </Link>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-start text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                  <th className="px-5 py-3 text-start font-semibold">Kampaniya</th>
                  <th className="px-5 py-3 text-start font-semibold">Endirim</th>
                  <th className="px-5 py-3 text-start font-semibold">Əhatə</th>
                  <th className="px-5 py-3 text-start font-semibold">Müddət</th>
                  <th className="px-5 py-3 text-start font-semibold">Vəziyyət</th>
                  <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {discounts.content.map((discount) => (
                  <tr key={discount.id} className="transition-colors hover:bg-[var(--color-muted)]">
                    <td className="px-5 py-3.5">
                      <Link
                        to={`/admin/discounts/${discount.id}`}
                        className="font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                      >
                        {discount.name}
                      </Link>
                      {discount.badgeLabel && (
                        <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                          Lent: {discount.badgeLabel}
                        </p>
                      )}
                    </td>

                    <td className="px-5 py-3.5">
                      <span className="font-semibold tabular text-[var(--color-sale)]">
                        {discount.discountType === 'PERCENTAGE'
                          ? `−${discount.value}%`
                          : `−${formatNumber(discount.value)} ₼`}
                      </span>
                      {discount.priority > 0 && (
                        <p className="text-xs text-[var(--color-subtle)]">
                          prioritet {discount.priority}
                        </p>
                      )}
                    </td>

                    <td className="px-5 py-3.5 text-[var(--color-body)]">
                      {SCOPE_LABELS[discount.scope]}
                      {discount.scope !== 'ALL_PRODUCTS' && (
                        <span className="text-[var(--color-subtle)]"> ({discount.targetCount})</span>
                      )}
                    </td>

                    <td className="px-5 py-3.5 text-xs text-[var(--color-body)]">
                      {discount.startsAt || discount.endsAt ? (
                        <>
                          {discount.startsAt ? formatDate(discount.startsAt) : 'İndi'}
                          {' → '}
                          {discount.endsAt ? formatDate(discount.endsAt) : 'süresiz'}
                        </>
                      ) : (
                        <span className="text-[var(--color-subtle)]">Müddətsiz</span>
                      )}
                    </td>

                    <td className="px-5 py-3.5">
                      <StateBadge discount={discount} />
                    </td>

                    <td className="px-5 py-3.5">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          title={discount.status === 'ACTIVE' ? 'Dayandır' : 'Aktivləşdir'}
                          onClick={() =>
                            changeStatus.mutate({
                              id: discount.id,
                              next: discount.status === 'ACTIVE' ? 'DRAFT' : 'ACTIVE',
                            })
                          }
                          icon={
                            discount.status === 'ACTIVE' ? (
                              <Pause size={15} aria-hidden="true" />
                            ) : (
                              <Play size={15} aria-hidden="true" />
                            )
                          }
                        />
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Sil"
                          className="text-[var(--color-danger)]"
                          onClick={() => {
                            if (window.confirm(`"${discount.name}" kampaniyası silinsin?`)) {
                              remove.mutate(discount.id);
                            }
                          }}
                          icon={<Trash2 size={15} aria-hidden="true" />}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {discounts && discounts.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
            <span className="text-xs text-[var(--color-subtle)]">
              {formatNumber(discounts.totalElements)} kampaniya
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={discounts.first}
                onClick={() => setPage((current) => current - 1)}
              >
                Əvvəlki
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={discounts.last}
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

/**
 * Four distinct states an operator actually cares about, derived from status + clock.
 */
function StateBadge({ discount }: { discount: DiscountSummary }) {
  let tone: BadgeTone = 'neutral';
  let label = 'Qaralama';
  let dot = false;

  if (discount.status === 'ARCHIVED') {
    tone = 'neutral';
    label = 'Arxiv';
  } else if (discount.running) {
    tone = 'success';
    label = 'İşləyir';
    dot = true;
  } else if (discount.scheduled) {
    tone = 'info';
    label = 'Planlaşdırılıb';
  } else if (discount.expired) {
    tone = 'neutral';
    label = 'Vaxtı bitib';
  } else if (discount.status === 'ACTIVE') {
    tone = 'warning';
    label = 'Aktiv, uyğun deyil';
  }

  return (
    <Badge tone={tone}>
      {dot && (
        <span
          className={clsx('h-1.5 w-1.5 rounded-full bg-current', 'animate-pulse')}
          aria-hidden="true"
        />
      )}
      {label}
    </Badge>
  );
}
