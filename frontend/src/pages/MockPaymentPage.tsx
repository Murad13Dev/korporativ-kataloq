import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CreditCard, Loader2, ShieldCheck } from 'lucide-react';
import { apiClient } from '@/lib/api';
import { formatPrice } from '@/lib/format';

/**
 * Stand-in for a bank's 3-D Secure page, used by the built-in `mock` payment provider.
 *
 * It exists so the entire payment flow — initiate → redirect → signed callback → order
 * confirmation — can be exercised end to end without a merchant agreement. The buttons
 * post the same signed parameters a real provider would send to our callback endpoint.
 *
 * This page is inert unless the backend is running the mock provider: the signature it
 * echoes back is generated server-side and is only valid for that provider's secret.
 */
export default function MockPaymentPage() {
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reference = searchParams.get('reference') ?? '';
  const transactionId = searchParams.get('transactionId') ?? '';
  const amount = searchParams.get('amount') ?? '0';
  const currency = searchParams.get('currency') ?? 'AZN';
  const signature = searchParams.get('signature') ?? '';

  const complete = async (status: 'SUCCESS' | 'DECLINED' | 'CANCELLED') => {
    setSubmitting(status);
    setError(null);
    try {
      await apiClient.post(
        '/public/payments/callback/mock',
        { reference, transactionId, amount, status, signature },
        { headers: { 'X-Signature': signature } },
      );
      window.location.href = `/checkout/result?reference=${encodeURIComponent(reference)}`;
    } catch {
      setError('Ödəniş emal edilə bilmədi. Yenidən cəhd edin.');
      setSubmitting(null);
    }
  };

  return (
    <div className="container-site flex justify-center py-16">
      <div className="w-full max-w-md">
        <div className="mb-4 flex items-center justify-center gap-2 text-sm text-[var(--color-subtle)]">
          <ShieldCheck size={16} aria-hidden="true" />
          Test ödəniş səhifəsi
        </div>

        <div className="card p-6">
          <div className="mb-6 flex items-center gap-3 border-b border-[var(--color-border)] pb-5">
            <div className="flex h-11 w-11 items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)]/10">
              <CreditCard size={20} className="text-[var(--color-primary)]" aria-hidden="true" />
            </div>
            <div>
              <h1 className="text-base font-bold">Ödəniş təsdiqi</h1>
              <p className="text-xs text-[var(--color-subtle)]">Mock provider (test rejimi)</p>
            </div>
          </div>

          <dl className="mb-6 space-y-2.5 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-body)]">Referans</dt>
              <dd className="font-mono text-xs">{reference}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-body)]">Tranzaksiya</dt>
              <dd className="font-mono text-xs">{transactionId}</dd>
            </div>
            <div className="flex items-baseline justify-between border-t border-[var(--color-border)] pt-3">
              <dt className="font-semibold">Məbləğ</dt>
              <dd className="font-display text-2xl font-bold tabular">
                {formatPrice(Number(amount))} {currency !== 'AZN' && currency}
              </dd>
            </div>
          </dl>

          {error && (
            <p className="mb-4 rounded-[var(--radius-button)] bg-[var(--color-danger)]/10 px-3 py-2 text-sm text-[var(--color-danger)]">
              {error}
            </p>
          )}

          <div className="space-y-2.5">
            <button
              type="button"
              onClick={() => complete('SUCCESS')}
              disabled={submitting !== null}
              className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-success)] px-6 py-3 text-sm font-semibold text-white disabled:opacity-60"
            >
              {submitting === 'SUCCESS' && (
                <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              )}
              Uğurlu ödənişi simulyasiya et
            </button>

            <button
              type="button"
              onClick={() => complete('DECLINED')}
              disabled={submitting !== null}
              className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-danger)] px-6 py-3 text-sm font-semibold text-[var(--color-danger)] disabled:opacity-60"
            >
              {submitting === 'DECLINED' && (
                <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              )}
              İmtina edilmiş ödənişi simulyasiya et
            </button>

            <button
              type="button"
              onClick={() => complete('CANCELLED')}
              disabled={submitting !== null}
              className="w-full rounded-[var(--radius-button)] px-6 py-3 text-sm font-medium text-[var(--color-subtle)] disabled:opacity-60"
            >
              Ləğv et
            </button>
          </div>
        </div>

        <p className="mt-4 text-center text-xs text-[var(--color-subtle)}">
          Real bank inteqrasiyası üçün <code>PAYMENT_PROVIDER</code> dəyişənini dəyişin.
        </p>
      </div>
    </div>
  );
}
