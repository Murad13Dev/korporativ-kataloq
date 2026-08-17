import { useForm } from 'react-hook-form';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Bell, BellOff, BellRing, Copy, Gift, KeyRound, PiggyBank, Save } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { AnimatedCounter } from '@/components/ui/AnimatedCounter';
import { useToast } from '@/components/ui/Toast';
import { useCustomerAuth, type UpdateProfilePayload } from '@/hooks/useCustomerAuth';
import { usePushSubscription } from '@/hooks/usePushSubscription';
import { apiClient, ApiError, getData } from '@/lib/api';
import { formatPrice } from '@/lib/format';

export default function AccountProfilePage() {
  return (
    <div className="space-y-6">
      <SavingsWallet />
      <ReferralCard />
      <PushNotificationCard />
      <ProfileForm />
      <PasswordForm />
    </div>
  );
}

/**
 * Opt-in browser push for order-status changes (shipped / delivered / cancelled /
 * refunded) — see PushNotificationService on the backend for the sender side. Hidden
 * entirely on unsupported browsers rather than showing a toggle that can never work.
 */
function PushNotificationCard() {
  const { state, subscribe, unsubscribe } = usePushSubscription();
  const toast = useToast();

  if (state === 'unsupported' || state === 'checking') {
    return null;
  }

  const isSubscribed = state === 'subscribed';

  const onToggle = async () => {
    try {
      if (isSubscribed) {
        await unsubscribe();
        toast.success('Push bildirişləri deaktiv edildi');
      } else {
        await subscribe();
        if (Notification.permission === 'granted') {
          toast.success('Push bildirişləri aktivləşdirildi');
        } else {
          toast.error('Bildiriş icazəsi verilmədi');
        }
      }
    } catch {
      toast.error('Bir xəta baş verdi, yenidən cəhd edin');
    }
  };

  return (
    <div className="card p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          {isSubscribed ? (
            <BellRing size={20} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
          ) : (
            <Bell size={20} className="mt-0.5 shrink-0 text-[var(--color-subtle)]" aria-hidden="true" />
          )}
          <div>
            <h2 className="text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
              Sifariş bildirişləri
            </h2>
            <p className="mt-1 text-sm text-[var(--color-body)]">
              Sifarişiniz göndərildikdə, çatdırıldıqda və ya statusu dəyişdikdə brauzer bildirişi alın.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void onToggle()}
          className={
            isSubscribed
              ? 'flex shrink-0 items-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2.5 text-sm font-semibold text-[var(--color-heading)] transition-colors hover:bg-[var(--color-muted)]'
              : 'flex shrink-0 items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]'
          }
        >
          {isSubscribed ? <BellOff size={15} aria-hidden="true" /> : <Bell size={15} aria-hidden="true" />}
          {isSubscribed ? 'Deaktiv et' : 'Aktivləşdir'}
        </button>
      </div>
    </div>
  );
}

/**
 * "Invite a friend" — the referral code is generated server-side (see
 * CustomerAuthService#profile, which self-heals accounts created before this feature
 * existed) so it's always present on `customer` once the profile has loaded once.
 */
function ReferralCard() {
  const { customer } = useCustomerAuth();
  const toast = useToast();

  if (!customer?.referralCode) {
    return null;
  }

  const link = `${window.location.origin}/hesab/qeydiyyat?ref=${customer.referralCode}`;

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(link);
      toast.success('Dəvət linki kopyalandı');
    } catch {
      toast.error('Kopyalanmadı');
    }
  };

  return (
    <div className="card p-6">
      <h2 className="mb-1.5 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
        <Gift size={16} aria-hidden="true" />
        Dostunu dəvət et
      </h2>
      <p className="mb-4 text-sm text-[var(--color-body)]">
        Dostunuz bu linklə qeydiyyatdan keçib ilk sifarişini tamamlasın — hər ikiniz endirim promo kodu qazanırsınız.
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <code className="min-w-0 flex-1 truncate rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-muted)] px-3 py-2.5 text-sm">
          {link}
        </code>
        <button
          type="button"
          onClick={() => void copyLink()}
          className="flex shrink-0 items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
        >
          <Copy size={15} aria-hidden="true" />
          Kopyala
        </button>
      </div>
    </div>
  );
}

interface SavingsSummary {
  lifetimeSavings: number;
  yearSavings: number;
}

/**
 * "Savings Wallet" — the total of every campaign, promo code and corporate discount
 * across a customer's real order history, not a marketing estimate. Rendered first on
 * the account landing page because it's the one number a returning shopper actually
 * wants to see: proof that shopping here paid off.
 */
function SavingsWallet() {
  const query = useQuery({
    queryKey: ['customer', 'savings'],
    queryFn: () => getData<SavingsSummary>('/customer/savings'),
  });

  const data = query.data;
  if (!query.isLoading && (!data || data.lifetimeSavings <= 0)) {
    return null;
  }

  return (
    <div className="card relative overflow-hidden p-6 text-center">
      <div className="gradient-mesh opacity-40" aria-hidden="true" />
      <div className="relative">
        <PiggyBank size={26} className="mx-auto mb-2 text-[var(--color-success)]" aria-hidden="true" />
        {query.isLoading || !data ? (
          <div className="skeleton mx-auto h-10 w-40" />
        ) : (
          <p className="font-display text-4xl font-bold tabular text-[var(--color-success)]">
            <AnimatedCounter value={formatPrice(data.lifetimeSavings)} />
          </p>
        )}
        <p className="mt-1 text-sm text-[var(--color-subtle)]">ümumi qənaətiniz</p>
        {data && data.yearSavings > 0 && (
          <p className="mt-2 text-xs text-[var(--color-subtle)]">
            Bu il: <span className="font-semibold text-[var(--color-heading)]">{formatPrice(data.yearSavings)}</span>{' '}
            qənaət etdiniz
          </p>
        )}
      </div>
    </div>
  );
}

function ProfileForm() {
  const { customer, updateProfile } = useCustomerAuth();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<UpdateProfilePayload>({
    values: customer
      ? {
          firstName: customer.firstName,
          lastName: customer.lastName,
          phone: customer.phone ?? '',
          companyName: customer.companyName ?? '',
          customerType: customer.customerType,
          marketingOptIn: customer.marketingOptIn,
        }
      : undefined,
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await updateProfile(values);
      toast.success('Profil yeniləndi');
      reset(values);
    } catch (error) {
      if (error instanceof ApiError) {
        toast.error(error.message);
      }
    }
  });

  return (
    <form onSubmit={onSubmit} className="card p-5 sm:p-6" noValidate>
      <h2 className="mb-4 text-lg font-bold">Profil məlumatları</h2>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Ad" error={errors.firstName?.message} required>
          <input
            {...register('firstName', { required: 'Ad tələb olunur' })}
            className={inputClass(Boolean(errors.firstName))}
          />
        </Field>
        <Field label="Soyad" error={errors.lastName?.message} required>
          <input
            {...register('lastName', { required: 'Soyad tələb olunur' })}
            className={inputClass(Boolean(errors.lastName))}
          />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <input
            {...register('phone')}
            type="tel"
            placeholder="+994 50 000 00 00"
            className={inputClass(Boolean(errors.phone))}
          />
        </Field>
        <Field label="Şirkət adı (istəyə bağlı)" error={errors.companyName?.message}>
          <input {...register('companyName')} className={inputClass(Boolean(errors.companyName))} />
        </Field>
        <Field label="E-poçt" className="sm:col-span-2">
          <input
            value={customer?.email ?? ''}
            disabled
            className={`${inputClass(false)} cursor-not-allowed opacity-60`}
          />
        </Field>
      </div>

      <label className="mt-4 flex cursor-pointer items-start gap-2.5 text-sm">
        <input
          type="checkbox"
          {...register('marketingOptIn')}
          className="mt-0.5 h-4 w-4 rounded accent-[var(--color-primary)]"
        />
        <span className="text-[var(--color-body)]">Endirim və kampaniyalardan xəbərdar olmaq istəyirəm</span>
      </label>

      <button
        type="submit"
        disabled={isSubmitting || !isDirty}
        className="mt-5 flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-50"
      >
        <Save size={16} aria-hidden="true" />
        Yadda saxla
      </button>
    </form>
  );
}

interface PasswordFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

function PasswordForm() {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    watch,
    setError,
    formState: { errors },
  } = useForm<PasswordFormValues>();

  const mutation = useMutation({
    mutationFn: (values: PasswordFormValues) =>
      apiClient.put('/customer/auth/me/password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      }),
    onSuccess: () => {
      toast.success('Şifrə dəyişdirildi');
      reset();
    },
    onError: (error: ApiError) => {
      if (error.is('INVALID_CREDENTIALS')) {
        setError('currentPassword', { message: 'Hazırkı şifrə yanlışdır' });
      } else {
        toast.error(error.message);
      }
    },
  });

  const onSubmit = handleSubmit((values) => {
    if (values.newPassword !== values.confirmPassword) {
      setError('confirmPassword', { message: 'Şifrələr uyğun gəlmir' });
      return;
    }
    mutation.mutate(values);
  });

  // Only needed to validate that confirmPassword tracks newPassword's live value.
  watch('newPassword');

  return (
    <form onSubmit={onSubmit} className="card p-5 sm:p-6" noValidate>
      <h2 className="mb-4 text-lg font-bold">Şifrəni dəyişmək</h2>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Hazırkı şifrə" error={errors.currentPassword?.message} required>
          <input
            {...register('currentPassword', { required: 'Hazırkı şifrə tələb olunur' })}
            type="password"
            autoComplete="current-password"
            className={inputClass(Boolean(errors.currentPassword))}
          />
        </Field>
        <Field label="Yeni şifrə" error={errors.newPassword?.message} required>
          <input
            {...register('newPassword', {
              required: 'Yeni şifrə tələb olunur',
              minLength: { value: 10, message: 'Ən azı 10 simvol' },
            })}
            type="password"
            autoComplete="new-password"
            className={inputClass(Boolean(errors.newPassword))}
          />
        </Field>
        <Field label="Yeni şifrə (təkrar)" error={errors.confirmPassword?.message} required>
          <input
            {...register('confirmPassword', { required: 'Şifrəni təkrarlayın' })}
            type="password"
            autoComplete="new-password"
            className={inputClass(Boolean(errors.confirmPassword))}
          />
        </Field>
      </div>

      <button
        type="submit"
        disabled={mutation.isPending}
        className="mt-5 flex items-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] px-5 py-2.5 text-sm font-semibold text-[var(--color-heading)] transition-colors hover:bg-[var(--color-muted)] disabled:opacity-50"
      >
        <KeyRound size={16} aria-hidden="true" />
        Şifrəni yenilə
      </button>
    </form>
  );
}
