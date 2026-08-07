import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Award, Copy, Gift } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, getPage, postData } from '@/lib/api';
import { formatDateTime, formatPrice } from '@/lib/format';

interface Balance {
  points: number;
  minRedeemable: number;
  pointsPerBlock: number;
  valuePerBlock: number;
}

interface Transaction {
  points: number;
  type: 'EARNED' | 'REDEEMED';
  description: string | null;
  createdAt: string;
}

interface RedeemResult {
  promoCode: string;
  amount: number;
  remainingPoints: number;
}

/**
 * Loyalty points — a ledger and a redeem button, nothing more.
 *
 * Redemption mints an ordinary promo code (see the backend service) rather than a
 * special checkout discount path, so applying it is identical to typing in any other
 * voucher on the cart page — one less thing for a shopper to learn.
 */
export default function AccountLoyaltyPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [blocks, setBlocks] = useState(1);
  const [redeemed, setRedeemed] = useState<RedeemResult | null>(null);

  const balance = useQuery({
    queryKey: ['customer', 'loyalty', 'balance'],
    queryFn: () => getData<Balance>('/customer/loyalty'),
  });

  const history = useQuery({
    queryKey: ['customer', 'loyalty', 'history'],
    queryFn: () => getPage<Transaction>('/customer/loyalty/history', { size: 20 }),
  });

  const redeem = useMutation({
    mutationFn: (points: number) => postData<RedeemResult>('/customer/loyalty/redeem', { points }),
    onSuccess: (result) => {
      setRedeemed(result);
      queryClient.invalidateQueries({ queryKey: ['customer', 'loyalty'] });
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (balance.isLoading || !balance.data) {
    return <SectionLoader label="Xallar yüklənir…" />;
  }

  const data = balance.data;
  const pointsNeeded = blocks * data.pointsPerBlock;
  const canRedeem = data.points >= pointsNeeded;

  const copyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      toast.success('Kod kopyalandı');
    } catch {
      toast.error('Kopyalanmadı');
    }
  };

  return (
    <div className="space-y-5">
      <div className="card p-6 text-center">
        <Award size={28} className="mx-auto mb-2 text-[var(--color-primary)]" aria-hidden="true" />
        <p className="font-display text-4xl font-bold tabular">{data.points}</p>
        <p className="text-sm text-[var(--color-subtle)]">xal balansı</p>
        <p className="mt-2 text-xs text-[var(--color-subtle)]">
          Hər {data.pointsPerBlock} xal {formatPrice(data.valuePerBlock)} endirimə çevrilir. Hər sifariş üçün
          xərclənən hər manata 1 xal qazanılır.
        </p>
      </div>

      <div className="card p-6">
        <h2 className="mb-4 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
          <Gift size={16} aria-hidden="true" />
          Xalları promo koda çevir
        </h2>

        {redeemed ? (
          <div className="rounded-[var(--radius-card)] border border-[var(--color-success)]/30 bg-[var(--color-success)]/5 p-4 text-center">
            <p className="text-sm text-[var(--color-body)]">
              {formatPrice(redeemed.amount)} endirim kodu yaradıldı:
            </p>
            <div className="mt-2 flex items-center justify-center gap-2">
              <code className="rounded-[var(--radius-button)] bg-[var(--color-muted)] px-3 py-1.5 font-mono text-sm font-semibold">
                {redeemed.promoCode}
              </code>
              <button
                type="button"
                onClick={() => void copyCode(redeemed.promoCode)}
                aria-label="Kodu kopyala"
                className="rounded-[var(--radius-button)] p-1.5 text-[var(--color-body)] hover:bg-[var(--color-muted)]"
              >
                <Copy size={15} aria-hidden="true" />
              </button>
            </div>
            <p className="mt-2 text-xs text-[var(--color-subtle)]">
              Bu kodu səbətdə promo kod sahəsinə daxil edin.
            </p>
            <button
              type="button"
              onClick={() => setRedeemed(null)}
              className="mt-3 text-xs font-medium text-[var(--color-primary)] hover:underline"
            >
              Yenidən çevir
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-[var(--color-heading)]">
                Neçə blok ({data.pointsPerBlock} xal = {formatPrice(data.valuePerBlock)})
              </label>
              <input
                type="number"
                min={1}
                max={Math.max(1, Math.floor(data.points / data.pointsPerBlock))}
                value={blocks}
                onChange={(event) => setBlocks(Math.max(1, Number(event.target.value)))}
                className="w-28 rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
              />
            </div>
            <button
              type="button"
              disabled={!canRedeem || redeem.isPending}
              onClick={() => redeem.mutate(pointsNeeded)}
              className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] disabled:opacity-50"
            >
              {pointsNeeded} xal çevir
            </button>
            {!canRedeem && (
              <p className="w-full text-xs text-[var(--color-danger)]">Kifayət qədər xalınız yoxdur</p>
            )}
          </div>
        )}
      </div>

      <div className="card overflow-hidden">
        <h2 className="border-b border-[var(--color-border)] px-5 py-4 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
          Tarixçə
        </h2>
        {history.isLoading ? (
          <SectionLoader label="Tarixçə yüklənir…" />
        ) : !history.data || history.data.content.length === 0 ? (
          <p className="p-6 text-center text-sm text-[var(--color-subtle)]">Hələ əməliyyat yoxdur</p>
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {history.data.content.map((entry, index) => (
              <li key={index} className="flex items-center justify-between gap-3 px-5 py-3 text-sm">
                <div className="min-w-0">
                  <p className="text-[var(--color-heading)]">{entry.description ?? (entry.type === 'EARNED' ? 'Qazanılan xal' : 'Xərclənən xal')}</p>
                  <p className="text-xs text-[var(--color-subtle)] tabular">{formatDateTime(entry.createdAt)}</p>
                </div>
                <span
                  className={`shrink-0 font-semibold tabular ${entry.points > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-danger)]'}`}
                >
                  {entry.points > 0 ? '+' : ''}
                  {entry.points}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
