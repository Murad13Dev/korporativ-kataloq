import { Link, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { getData } from '@/lib/api';
import { formatPrice } from '@/lib/format';
import { cartQueryKey } from '@/hooks/useCart';
import { useEffect } from 'react';
import type { PaymentStatusView } from '@/types/api';

/**
 * Where the shopper lands after paying (or after a cash-on-delivery order).
 *
 * The provider's webhook and the shopper's browser race each other, and the browser
 * usually wins. So this page polls the payment status for a short while rather than
 * trusting whatever the redirect said — the redirect is a hint, the server is the truth.
 */
export default function CheckoutResultPage() {
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const reference = searchParams.get('reference');
  const orderNumber = searchParams.get('orderNumber');

  const statusQuery = useQuery({
    queryKey: ['payment-status', reference],
    queryFn: () => getData<PaymentStatusView>(`/public/payments/${reference}/status`),
    enabled: Boolean(reference),
    // Poll while the payment is still in flight, then stop.
    refetchInterval: (query) => {
      const status = query.state.data?.paymentStatus;
      return status && ['SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED'].includes(status)
        ? false
        : 3000;
    },
    // Give up after a minute rather than polling forever on a stuck payment.
    retry: 3,
  });

  // The cart was consumed server-side at checkout; drop the stale client copy.
  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: cartQueryKey });
  }, [queryClient]);

  // Cash on delivery: no payment reference, the order is already confirmed.
  if (!reference) {
    return (
      <ResultShell
        tone="success"
        title="Sifarişiniz qəbul edildi"
        message={
          orderNumber
            ? `Sifariş nömrəniz: ${orderNumber}. Təsdiq məktubu e-poçtunuza göndərildi.`
            : 'Təsdiq məktubu e-poçtunuza göndərildi.'
        }
      />
    );
  }

  if (statusQuery.isLoading) {
    return <SectionLoader label="Ödəniş yoxlanılır…" />;
  }

  const status = statusQuery.data;

  if (!status) {
    return (
      <ResultShell
        tone="error"
        title="Ödəniş məlumatı tapılmadı"
        message="Ödəniş barədə məlumat əldə edilə bilmədi. Sifarişlərinizi hesabınızdan yoxlaya bilərsiniz."
      />
    );
  }

  if (status.paymentStatus === 'SUCCEEDED') {
    return (
      <ResultShell
        tone="success"
        title="Ödəniş uğurla tamamlandı"
        message={`${status.orderNumber} nömrəli sifarişiniz təsdiqləndi.`}
        amount={formatPrice(status.total)}
        orderNumber={status.orderNumber}
      />
    );
  }

  if (status.paymentStatus === 'FAILED' || status.paymentStatus === 'CANCELLED') {
    return (
      <ResultShell
        tone="error"
        title={status.paymentStatus === 'CANCELLED' ? 'Ödəniş ləğv edildi' : 'Ödəniş uğursuz oldu'}
        message={
          status.failureMessage ??
          'Ödəniş tamamlanmadı. Sifarişiniz saxlanılıb — yenidən cəhd edə bilərsiniz.'
        }
        orderNumber={status.orderNumber}
        retryOrderId={status.orderId}
      />
    );
  }

  return (
    <ResultShell
      tone="pending"
      title="Ödəniş emal olunur"
      message="Bankdan təsdiq gözlənilir. Bu səhifəni bağlamayın — status avtomatik yenilənəcək."
      orderNumber={status.orderNumber}
    />
  );
}

function ResultShell({
  tone,
  title,
  message,
  amount,
  orderNumber,
  retryOrderId,
}: {
  tone: 'success' | 'error' | 'pending';
  title: string;
  message: string;
  amount?: string;
  orderNumber?: string;
  retryOrderId?: number;
}) {
  const config = {
    success: { Icon: CheckCircle2, color: 'var(--color-success)' },
    error: { Icon: XCircle, color: 'var(--color-danger)' },
    pending: { Icon: Clock, color: 'var(--color-warning)' },
  }[tone];

  const { Icon, color } = config;

  return (
    <div className="container-site flex flex-col items-center py-20 text-center">
      <div
        className="mb-6 flex h-16 w-16 items-center justify-center rounded-full"
        style={{ backgroundColor: `color-mix(in oklab, ${color} 12%, transparent)` }}
      >
        <Icon size={30} style={{ color }} aria-hidden="true" />
      </div>

      <h1 className="mb-3 text-2xl font-bold lg:text-3xl">{title}</h1>
      <p className="mb-6 max-w-md text-[var(--color-body)]">{message}</p>

      {amount && (
        <p className="mb-6 font-display text-3xl font-bold tabular text-[var(--color-heading)]">
          {amount}
        </p>
      )}

      {orderNumber && (
        <p className="mb-8 rounded-[var(--radius-button)] bg-[var(--color-muted)] px-4 py-2 text-sm">
          Sifariş nömrəsi: <strong className="tabular">{orderNumber}</strong>
        </p>
      )}

      <div className="flex flex-wrap justify-center gap-3">
        {retryOrderId && (
          <Link
            to={`/checkout?retry=${retryOrderId}`}
            className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)]"
          >
            Yenidən cəhd et
          </Link>
        )}
        <Link
          to="/kataloq"
          className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-6 py-3 text-sm font-semibold"
        >
          Alış-verişə davam et
        </Link>
      </div>
    </div>
  );
}
