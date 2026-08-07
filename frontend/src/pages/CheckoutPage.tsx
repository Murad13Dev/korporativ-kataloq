import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import clsx from 'clsx';
import { CreditCard, Loader2, Lock, Package, Truck } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { useCart } from '@/hooks/useCart';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { ApiError, postData } from '@/lib/api';
import { formatPrice } from '@/lib/format';
import type { CheckoutRequest, CheckoutResponse, DeliveryMethod, PaymentMethod } from '@/types/api';

const DELIVERY_OPTIONS: { value: DeliveryMethod; label: string; hint: string; icon: typeof Truck }[] = [
  { value: 'COURIER', label: 'Kuryerlə çatdırılma', hint: 'Ünvanınıza çatdırılır', icon: Truck },
  { value: 'PICKUP', label: 'Mağazadan götürmə', hint: 'Çatdırılma haqqı yoxdur', icon: Package },
];

export default function CheckoutPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { data: cart, isLoading } = useCart();
  const { features, commerce } = useSiteConfig();

  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod>('COURIER');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('ONLINE');

  const {
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<CheckoutRequest>({
    defaultValues: { country: 'Azərbaycan', termsAccepted: false },
  });

  const checkout = useMutation({
    mutationFn: (payload: CheckoutRequest) =>
      postData<CheckoutResponse>('/public/checkout', payload),
    onSuccess: (response) => {
      // Online payment hands the browser to the provider; anything else lands
      // straight on the result page.
      if (response.paymentUrl) {
        window.location.href = response.paymentUrl;
        return;
      }
      navigate(`/checkout/result?orderNumber=${response.orderNumber}`, { replace: true });
    },
    onError: (error: ApiError) => {
      // Field-level errors are attached to their inputs; the rest becomes a toast.
      const fields = Object.entries(error.fieldErrors);
      if (fields.length > 0) {
        fields.forEach(([field, message]) =>
          setError(field as keyof CheckoutRequest, { message }),
        );
      } else {
        toast.error(error.message);
      }
    },
  });

  if (isLoading) {
    return <SectionLoader label="Səbət yoxlanılır…" />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="container-site py-24 text-center">
        <h1 className="mb-3 text-2xl font-bold">Səbətiniz boşdur</h1>
        <Link to="/kataloq" className="text-[var(--color-primary)] hover:underline">
          Kataloqa qayıt
        </Link>
      </div>
    );
  }

  const paymentOptions = (commerce?.paymentMethods ?? ['ONLINE', 'CASH_ON_DELIVERY']).filter(
    (method) => method !== 'ONLINE' || features.onlinePaymentEnabled,
  );

  const onSubmit = handleSubmit((values) => {
    checkout.mutate({ ...values, deliveryMethod, paymentMethod });
  });

  return (
    <div className="container-site py-8 lg:py-12">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">Sifarişi tamamla</h1>

      <form onSubmit={onSubmit} className="grid gap-8 lg:grid-cols-[1fr_360px]" noValidate>
        <div className="space-y-6">
          {/* ---------------- contact ---------------- */}
          <fieldset className="card p-5">
            <legend className="mb-4 px-1 text-lg font-bold">Əlaqə məlumatları</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Ad və soyad" error={errors.fullName?.message} required>
                <input
                  {...register('fullName', { required: 'Ad və soyad tələb olunur' })}
                  autoComplete="name"
                  className={inputClass(Boolean(errors.fullName))}
                />
              </Field>

              <Field label="Telefon" error={errors.phone?.message} required>
                <input
                  {...register('phone', { required: 'Telefon tələb olunur' })}
                  type="tel"
                  autoComplete="tel"
                  placeholder="+994 50 000 00 00"
                  className={inputClass(Boolean(errors.phone))}
                />
              </Field>

              <Field label="E-poçt" error={errors.email?.message} required className="sm:col-span-2">
                <input
                  {...register('email', {
                    required: 'E-poçt tələb olunur',
                    pattern: { value: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: 'E-poçt formatı yanlışdır' },
                  })}
                  type="email"
                  autoComplete="email"
                  className={inputClass(Boolean(errors.email))}
                />
              </Field>
            </div>
          </fieldset>

          {/* ---------------- delivery ---------------- */}
          <fieldset className="card p-5">
            <legend className="mb-4 px-1 text-lg font-bold">Çatdırılma</legend>

            <div className="mb-5 grid gap-3 sm:grid-cols-2">
              {DELIVERY_OPTIONS.map((option) => {
                const Icon = option.icon;
                const selected = deliveryMethod === option.value;
                return (
                  <label
                    key={option.value}
                    className={clsx(
                      'flex cursor-pointer items-start gap-3 rounded-[var(--radius-card)] border-2 p-4 transition-colors',
                      selected
                        ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/5'
                        : 'border-[var(--color-border)] hover:border-[var(--color-subtle)]',
                    )}
                  >
                    <input
                      type="radio"
                      name="deliveryMethod"
                      value={option.value}
                      checked={selected}
                      onChange={() => setDeliveryMethod(option.value)}
                      className="sr-only"
                    />
                    <Icon
                      size={19}
                      className={selected ? 'text-[var(--color-primary)]' : 'text-[var(--color-subtle)]'}
                      aria-hidden="true"
                    />
                    <span>
                      <span className="block text-sm font-semibold text-[var(--color-heading)]">
                        {option.label}
                      </span>
                      <span className="block text-xs text-[var(--color-subtle)]">{option.hint}</span>
                    </span>
                  </label>
                );
              })}
            </div>

            {/* Pickup needs no address, so the fields disappear entirely rather than
                being disabled — fewer inputs is a faster checkout. */}
            {deliveryMethod !== 'PICKUP' && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Şəhər" error={errors.city?.message} required>
                  <input
                    {...register('city', { required: 'Şəhər tələb olunur' })}
                    autoComplete="address-level2"
                    className={inputClass(Boolean(errors.city))}
                  />
                </Field>

                <Field label="Rayon">
                  <input {...register('district')} className={inputClass(false)} />
                </Field>

                <Field
                  label="Ünvan"
                  error={errors.addressLine?.message}
                  required
                  className="sm:col-span-2"
                >
                  <textarea
                    {...register('addressLine', { required: 'Ünvan tələb olunur' })}
                    rows={2}
                    autoComplete="street-address"
                    className={inputClass(Boolean(errors.addressLine))}
                  />
                </Field>
              </div>
            )}
          </fieldset>

          {/* ---------------- payment ---------------- */}
          <fieldset className="card p-5">
            <legend className="mb-4 px-1 text-lg font-bold">Ödəniş üsulu</legend>
            <div className="space-y-3">
              {paymentOptions.map((method) => {
                const selected = paymentMethod === method;
                return (
                  <label
                    key={method}
                    className={clsx(
                      'flex cursor-pointer items-center gap-3 rounded-[var(--radius-card)] border-2 p-4 transition-colors',
                      selected
                        ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/5'
                        : 'border-[var(--color-border)] hover:border-[var(--color-subtle)]',
                    )}
                  >
                    <input
                      type="radio"
                      name="paymentMethod"
                      value={method}
                      checked={selected}
                      onChange={() => setPaymentMethod(method)}
                      className="sr-only"
                    />
                    <CreditCard
                      size={19}
                      className={selected ? 'text-[var(--color-primary)]' : 'text-[var(--color-subtle)]'}
                      aria-hidden="true"
                    />
                    <span className="text-sm font-semibold text-[var(--color-heading)]">
                      {method === 'ONLINE'
                        ? 'Onlayn kart ödənişi'
                        : method === 'CASH_ON_DELIVERY'
                          ? 'Çatdırılma zamanı nağd'
                          : 'Bank köçürməsi'}
                    </span>
                  </label>
                );
              })}
            </div>

            <Field label="Qeyd (istəyə bağlı)" className="mt-5">
              <textarea {...register('customerNote')} rows={3} className={inputClass(false)} />
            </Field>
          </fieldset>
        </div>

        {/* ---------------- summary ---------------- */}
        <aside className="lg:sticky lg:top-[calc(var(--header-height)+1rem)] lg:self-start">
          <div className="card p-5">
            <h2 className="mb-4 text-lg font-bold">Sifarişiniz</h2>

            <ul className="mb-4 space-y-3 border-b border-[var(--color-border)] pb-4">
              {cart.items.map((line) => (
                <li key={line.id} className="flex items-start justify-between gap-3 text-sm">
                  <span className="min-w-0">
                    <span className="line-clamp-1 font-medium text-[var(--color-heading)]">
                      {line.name}
                    </span>
                    <span className="text-xs text-[var(--color-subtle)] tabular">
                      {line.quantity} × {formatPrice(line.unitPrice)}
                    </span>
                  </span>
                  <span className="shrink-0 font-semibold tabular">{formatPrice(line.lineTotal)}</span>
                </li>
              ))}
            </ul>

            <dl className="space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-[var(--color-body)]">Ara cəm</dt>
                <dd className="font-medium tabular">{formatPrice(cart.totals.subtotal)}</dd>
              </div>
              {cart.totals.promoDiscount > 0 && (
                <div className="flex justify-between text-[var(--color-success)]">
                  <dt>Promo kod</dt>
                  <dd className="font-medium tabular">− {formatPrice(cart.totals.promoDiscount)}</dd>
                </div>
              )}
              <div className="flex justify-between">
                <dt className="text-[var(--color-body)]">Çatdırılma</dt>
                <dd className="font-medium tabular">
                  {deliveryMethod === 'PICKUP' || cart.totals.freeDelivery
                    ? 'Pulsuz'
                    : formatPrice(cart.totals.deliveryPrice)}
                </dd>
              </div>
            </dl>

            <div className="mt-4 flex items-baseline justify-between border-t border-[var(--color-border)] pt-4">
              <span className="font-semibold">Yekun</span>
              <span className="font-display text-2xl font-bold tabular">
                {formatPrice(
                  deliveryMethod === 'PICKUP'
                    ? cart.totals.total - cart.totals.deliveryPrice
                    : cart.totals.total,
                )}
              </span>
            </div>

            <label className="mt-5 flex cursor-pointer items-start gap-2.5 text-sm">
              <input
                type="checkbox"
                {...register('termsAccepted', { required: 'Şərtlər qəbul edilməlidir' })}
                className="mt-0.5 h-4 w-4 rounded accent-[var(--color-primary)]"
              />
              <span className="text-[var(--color-body)]">
                <Link to="/istifade-sertleri" className="text-[var(--color-primary)] hover:underline">
                  İstifadə şərtləri
                </Link>{' '}
                ilə razıyam
              </span>
            </label>
            {errors.termsAccepted && (
              <p className="mt-1 text-xs text-[var(--color-danger)]">
                {errors.termsAccepted.message}
              </p>
            )}

            <button
              type="submit"
              disabled={checkout.isPending || !cart.checkoutAllowed}
              className="mt-5 flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
            >
              {checkout.isPending ? (
                <Loader2 size={17} className="animate-spin" aria-hidden="true" />
              ) : (
                <Lock size={15} aria-hidden="true" />
              )}
              {paymentMethod === 'ONLINE' ? 'Ödənişə keç' : 'Sifarişi təsdiqlə'}
            </button>

            <p className="mt-3 text-center text-xs text-[var(--color-subtle)]">
              Məlumatlarınız şifrələnmiş kanal ilə ötürülür.
            </p>
          </div>
        </aside>
      </form>
    </div>
  );
}

function Field({
  label,
  error,
  required,
  className,
  children,
}: {
  label: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={clsx('block', className)}>
      <span className="mb-1.5 block text-sm font-medium text-[var(--color-heading)]">
        {label}
        {required && <span className="ms-0.5 text-[var(--color-danger)]">*</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-[var(--color-danger)]">{error}</span>}
    </label>
  );
}

function inputClass(hasError: boolean): string {
  return clsx(
    'w-full rounded-[var(--radius-input)] border bg-[var(--color-canvas)] px-3 py-2.5 text-sm outline-none transition-colors',
    hasError
      ? 'border-[var(--color-danger)] focus:border-[var(--color-danger)]'
      : 'border-[var(--color-border)] focus:border-[var(--color-primary)]',
  );
}
