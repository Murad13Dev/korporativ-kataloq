import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CheckCircle2, FileText, XCircle } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, postVoid } from '@/lib/api';
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
  productId: number | null;
  productName: string;
  productSlug: string | null;
  quantity: number;
  quotedUnitPrice: number | null;
  lineTotal: number | null;
}

interface QuoteView extends QuoteRow {
  contactPhone: string | null;
  note: string | null;
  adminNote: string | null;
  validUntil: string | null;
  items: QuoteItem[];
}

const STATUS_LABEL: Record<QuoteStatus, string> = {
  PENDING: 'Gözləyir',
  QUOTED: 'Qiymət təklif olundu',
  ACCEPTED: 'Qəbul edildi',
  DECLINED: 'Rədd edildi',
  EXPIRED: 'Vaxtı bitib',
};

const STATUS_TONE: Record<QuoteStatus, string> = {
  PENDING: 'bg-[var(--color-warning)]/12 text-[var(--color-warning)]',
  QUOTED: 'bg-[var(--color-primary)]/12 text-[var(--color-primary)]',
  ACCEPTED: 'bg-[var(--color-success)]/12 text-[var(--color-success)]',
  DECLINED: 'bg-[var(--color-danger)]/12 text-[var(--color-danger)]',
  EXPIRED: 'bg-[var(--color-muted)] text-[var(--color-subtle)]',
};

export default function AccountQuotesPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const list = useQuery({
    queryKey: ['customer', 'quotes'],
    queryFn: () => getData<QuoteRow[]>('/customer/quote-requests'),
  });

  if (list.isLoading) {
    return <SectionLoader label="Sorğular yüklənir…" />;
  }

  if (!list.data || list.data.length === 0) {
    return (
      <div className="card flex flex-col items-center py-16 text-center">
        <FileText size={32} className="mb-3 text-[var(--color-subtle)]" aria-hidden="true" />
        <h2 className="text-lg font-bold text-[var(--color-heading)]">Hələ təklif sorğunuz yoxdur</h2>
        <p className="mt-1.5 max-w-sm text-sm text-[var(--color-body)]">
          Böyük həcmdə sifariş üçün səbət səhifəsindən topdan qiymət təklifi istəyə bilərsiniz.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <h2 className="text-lg font-bold text-[var(--color-heading)]">Təklif sorğuları</h2>
      <ul className="space-y-2">
        {list.data.map((quote) => (
          <li key={quote.id} className="card overflow-hidden">
            <button
              type="button"
              onClick={() => setSelectedId(selectedId === quote.id ? null : quote.id)}
              className="flex w-full items-center justify-between gap-3 p-4 text-start"
            >
              <div className="min-w-0">
                <p className="font-semibold text-[var(--color-heading)]">Sorğu #{quote.id}</p>
                <p className="text-xs text-[var(--color-subtle)]">
                  {quote.itemCount} məhsul · {formatDateTime(quote.createdAt)}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                {quote.quotedTotal != null && (
                  <span className="font-semibold tabular text-[var(--color-heading)]">
                    {formatPrice(quote.quotedTotal)}
                  </span>
                )}
                <span
                  className={clsx(
                    'rounded-full px-2.5 py-1 text-xs font-medium',
                    STATUS_TONE[quote.status],
                  )}
                >
                  {STATUS_LABEL[quote.status]}
                </span>
              </div>
            </button>

            {selectedId === quote.id && <QuoteDetail id={quote.id} />}
          </li>
        ))}
      </ul>
    </div>
  );
}

function QuoteDetail({ id }: { id: number }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const detail = useQuery({
    queryKey: ['customer', 'quotes', id],
    queryFn: () => getData<QuoteView>(`/customer/quote-requests/${id}`),
  });

  const respond = useMutation({
    mutationFn: (action: 'accept' | 'decline') => postVoid(`/customer/quote-requests/${id}/${action}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', 'quotes'] });
      toast.success('Cavabınız qeydə alındı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (detail.isLoading || !detail.data) {
    return <div className="border-t border-[var(--color-border)] p-4"><SectionLoader label="Yüklənir…" /></div>;
  }

  const quote = detail.data;

  return (
    <div className="border-t border-[var(--color-border)] p-4">
      <ul className="mb-3 space-y-1.5 text-sm">
        {quote.items.map((item) => (
          <li key={item.id} className="flex items-center justify-between gap-3">
            <span className="text-[var(--color-body)]">
              {item.quantity} × {item.productName}
            </span>
            {item.lineTotal != null ? (
              <span className="font-medium tabular text-[var(--color-heading)]">
                {formatPrice(item.lineTotal)}
              </span>
            ) : (
              <span className="text-xs text-[var(--color-subtle)]">Qiymət gözlənilir</span>
            )}
          </li>
        ))}
      </ul>

      {quote.note && (
        <p className="mb-3 rounded-[var(--radius-button)] bg-[var(--color-muted)] p-3 text-xs text-[var(--color-body)]">
          <strong>Sizin qeydiniz:</strong> {quote.note}
        </p>
      )}

      {quote.adminNote && (
        <p className="mb-3 rounded-[var(--radius-button)] bg-[var(--color-primary)]/8 p-3 text-xs text-[var(--color-body)]">
          <strong>Komandamızdan:</strong> {quote.adminNote}
        </p>
      )}

      {quote.validUntil && quote.status === 'QUOTED' && (
        <p className="mb-3 text-xs text-[var(--color-subtle)]">
          Təklif etibarlıdır: {formatDateTime(quote.validUntil)}
        </p>
      )}

      {quote.status === 'QUOTED' && (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => respond.mutate('accept')}
            disabled={respond.isPending}
            className="flex items-center gap-1.5 rounded-[var(--radius-button)] bg-[var(--color-success)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            <CheckCircle2 size={15} aria-hidden="true" />
            Qəbul et
          </button>
          <button
            type="button"
            onClick={() => respond.mutate('decline')}
            disabled={respond.isPending}
            className="flex items-center gap-1.5 rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2 text-sm font-medium text-[var(--color-body)] disabled:opacity-50"
          >
            <XCircle size={15} aria-hidden="true" />
            Rədd et
          </button>
        </div>
      )}
    </div>
  );
}
