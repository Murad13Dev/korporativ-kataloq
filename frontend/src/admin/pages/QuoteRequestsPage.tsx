import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText, Search } from 'lucide-react';
import { Badge, type BadgeTone, Button, Card, EmptyState, Input, PageHeader, Select, TableSkeleton } from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, getPage, postData } from '@/lib/api';
import { formatDateTime, formatPrice } from '@/lib/format';

type QuoteStatus = 'PENDING' | 'QUOTED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';

interface QuoteRow {
  id: number;
  contactName: string;
  contactEmail: string;
  status: QuoteStatus;
  itemCount: number;
  quotedTotal: number | null;
  currency: string;
  createdAt: string;
}

interface QuoteItem {
  id: number;
  productName: string;
  quantity: number;
  quotedUnitPrice: number | null;
  lineTotal: number | null;
}

interface QuoteDetail extends QuoteRow {
  contactPhone: string | null;
  note: string | null;
  adminNote: string | null;
  validUntil: string | null;
  items: QuoteItem[];
}

const STATUS_LABEL: Record<QuoteStatus, string> = {
  PENDING: 'Gözləyir',
  QUOTED: 'Təklif edildi',
  ACCEPTED: 'Qəbul edildi',
  DECLINED: 'Rədd edildi',
  EXPIRED: 'Vaxtı bitib',
};

const STATUS_TONE: Record<QuoteStatus, BadgeTone> = {
  PENDING: 'warning',
  QUOTED: 'info',
  ACCEPTED: 'success',
  DECLINED: 'danger',
  EXPIRED: 'neutral',
};

/** Request-for-quote inbox. Pricing a request moves it to QUOTED; the customer accepts/declines on their end. */
export function QuoteRequestsPage() {
  const [status, setStatus] = useState<QuoteStatus | ''>('');
  const [search, setSearch] = useState('');
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const quotes = useQuery({
    queryKey: ['admin', 'quotes', status, search],
    queryFn: () => getPage<QuoteRow>('/admin/quote-requests', { status: status || undefined, search: search || undefined, size: 50 }),
  });

  const rows = quotes.data;

  return (
    <>
      <PageHeader title="Təklif sorğuları" description="Topdan sifariş üçün qiymət sorğuları" />

      <div className="mb-4 flex flex-wrap gap-3">
        <div className="relative max-w-sm flex-1">
          <Search size={15} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]" aria-hidden="true" />
          <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Ad və ya e-poçt axtar…" className="ps-9" />
        </div>
        <Select value={status} onChange={(event) => setStatus(event.target.value as QuoteStatus | '')} className="w-48">
          <option value="">Bütün statuslar</option>
          <option value="PENDING">Gözləyir</option>
          <option value="QUOTED">Təklif edildi</option>
          <option value="ACCEPTED">Qəbul edildi</option>
          <option value="DECLINED">Rədd edildi</option>
          <option value="EXPIRED">Vaxtı bitib</option>
        </Select>
      </div>

      <Card bodyClassName="p-0">
        {quotes.isLoading ? (
          <TableSkeleton rows={5} columns={4} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState icon={<FileText size={32} aria-hidden="true" />} title="Sorğu yoxdur" />
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {rows.content.map((quote) => (
              <li key={quote.id}>
                <button
                  type="button"
                  onClick={() => setExpandedId(expandedId === quote.id ? null : quote.id)}
                  className="flex w-full flex-wrap items-center justify-between gap-3 px-5 py-4 text-start hover:bg-[var(--color-muted)]"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-[var(--color-heading)]">{quote.contactName}</span>
                      <Badge tone={STATUS_TONE[quote.status]}>{STATUS_LABEL[quote.status]}</Badge>
                    </div>
                    <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                      {quote.contactEmail} · {quote.itemCount} məhsul · {formatDateTime(quote.createdAt)}
                    </p>
                  </div>
                  {quote.quotedTotal != null && (
                    <span className="font-semibold tabular text-[var(--color-heading)]">
                      {formatPrice(quote.quotedTotal)}
                    </span>
                  )}
                </button>

                {expandedId === quote.id && <QuotePanel id={quote.id} />}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}

function QuotePanel({ id }: { id: number }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const detail = useQuery({
    queryKey: ['admin', 'quotes', id],
    queryFn: () => getData<QuoteDetail>(`/admin/quote-requests/${id}`),
  });

  const [prices, setPrices] = useState<Record<number, string>>({});
  const [validUntil, setValidUntil] = useState('');
  const [adminNote, setAdminNote] = useState('');

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'quotes'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'quotes', id] });
  };

  const submitQuote = useMutation({
    mutationFn: () =>
      postData(`/admin/quote-requests/${id}/quote`, {
        prices: Object.entries(prices)
          .filter(([, value]) => value.trim() !== '')
          .map(([itemId, value]) => ({ itemId: Number(itemId), unitPrice: Number(value) })),
        validUntil: validUntil ? new Date(validUntil).toISOString() : undefined,
        adminNote: adminNote || undefined,
      }),
    onSuccess: () => {
      invalidate();
      toast.success('Qiymət təklifi göndərildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const decline = useMutation({
    mutationFn: () => postData(`/admin/quote-requests/${id}/decline`, { adminNote: adminNote || undefined }),
    onSuccess: () => {
      invalidate();
      toast.success('Sorğu rədd edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (detail.isLoading || !detail.data) {
    return <div className="border-t border-[var(--color-border)] px-5 py-4"><TableSkeleton rows={2} columns={1} /></div>;
  }

  const quote = detail.data;
  const editable = quote.status === 'PENDING' || quote.status === 'QUOTED';

  return (
    <div className="space-y-4 border-t border-[var(--color-border)] bg-[var(--color-muted)]/40 px-5 py-4">
      {quote.note && (
        <p className="rounded-[var(--radius-button)] bg-[var(--color-surface)] p-3 text-sm text-[var(--color-body)]">
          <strong>Müştəri qeydi:</strong> {quote.note}
        </p>
      )}
      {quote.contactPhone && (
        <p className="text-xs text-[var(--color-subtle)]">Telefon: {quote.contactPhone}</p>
      )}

      <table className="w-full text-sm">
        <thead>
          <tr className="text-start text-xs uppercase tracking-wide text-[var(--color-subtle)]">
            <th className="pb-2 text-start font-medium">Məhsul</th>
            <th className="pb-2 text-start font-medium">Say</th>
            <th className="pb-2 text-start font-medium">Vahid qiymət</th>
          </tr>
        </thead>
        <tbody>
          {quote.items.map((item) => (
            <tr key={item.id} className="border-t border-[var(--color-border)]">
              <td className="py-2 text-[var(--color-heading)]">{item.productName}</td>
              <td className="py-2 tabular text-[var(--color-body)]">{item.quantity}</td>
              <td className="py-2">
                {editable ? (
                  <Input
                    type="number"
                    min={0}
                    step={0.01}
                    defaultValue={item.quotedUnitPrice ?? ''}
                    onChange={(event) => setPrices((current) => ({ ...current, [item.id]: event.target.value }))}
                    className="w-28"
                  />
                ) : (
                  <span className="tabular">{item.quotedUnitPrice != null ? formatPrice(item.quotedUnitPrice) : '—'}</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {editable && (
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-[var(--color-heading)]">Etibarlıdır (tarix)</label>
            <Input type="date" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} />
          </div>
          <div className="min-w-64 flex-1">
            <label className="mb-1 block text-xs font-medium text-[var(--color-heading)]">Qeyd</label>
            <Input value={adminNote} onChange={(event) => setAdminNote(event.target.value)} placeholder="Çatdırılma şərtləri və s." />
          </div>
          <Button loading={submitQuote.isPending} onClick={() => submitQuote.mutate()}>
            Qiymət təklif et
          </Button>
          <Button variant="secondary" loading={decline.isPending} onClick={() => decline.mutate()}>
            Rədd et
          </Button>
        </div>
      )}

      {quote.adminNote && !editable && (
        <p className="text-xs text-[var(--color-subtle)]">Qeyd: {quote.adminNote}</p>
      )}
    </div>
  );
}
