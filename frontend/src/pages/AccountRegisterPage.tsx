import { useEffect } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQueryClient } from '@tanstack/react-query';
import { UserPlus } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { useCustomerAuth, type RegisterPayload } from '@/hooks/useCustomerAuth';
import { cartQueryKey } from '@/hooks/useCart';
import { ApiError } from '@/lib/api';

export default function AccountRegisterPage() {
  const { status, restore, register: registerCustomer } = useCustomerAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const next = searchParams.get('next') ?? '/hesab/profil';
  const referralCode = searchParams.get('ref') ?? undefined;

  useEffect(() => {
    if (status === 'idle') {
      void restore();
    }
  }, [status, restore]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<RegisterPayload>({ defaultValues: { termsAccepted: false, marketingOptIn: false } });

  if (status === 'idle' || status === 'checking') {
    return <SectionLoader label="Yoxlanılır…" />;
  }

  if (status === 'authenticated') {
    return <Navigate to={next} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      await registerCustomer({ ...values, referralCode });
      await queryClient.invalidateQueries({ queryKey: cartQueryKey });
      navigate(next, { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        const fields = Object.entries(error.fieldErrors);
        if (error.is('EMAIL_ALREADY_EXISTS')) {
          setError('email', { message: 'Bu e-poçt ilə hesab artıq mövcuddur' });
        } else if (fields.length > 0) {
          fields.forEach(([field, message]) =>
            setError(field as keyof RegisterPayload, { message }),
          );
        } else {
          toast.error(error.message);
        }
      }
    }
  });

  return (
    <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
      <div className="card w-full max-w-md p-6 sm:p-8">
        <h1 className="mb-1 text-2xl font-bold tracking-tight">Qeydiyyat</h1>
        <p className="mb-6 text-sm text-[var(--color-subtle)]">
          Bir neçə addımda hesab yaradın.
        </p>

        {referralCode && (
          <p className="mb-5 rounded-[var(--radius-button)] border border-[var(--color-primary)]/30 bg-[var(--color-primary)]/5 px-3.5 py-2.5 text-sm text-[var(--color-primary)]">
            🎉 Dəvət kodu ilə qeydiyyatdan keçirsiniz — ilk sifarişinizdən sonra hər ikiniz endirim qazanacaqsınız.
          </p>
        )}

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Ad" error={errors.firstName?.message} required>
              <input
                {...register('firstName', { required: 'Ad tələb olunur' })}
                autoComplete="given-name"
                className={inputClass(Boolean(errors.firstName))}
              />
            </Field>
            <Field label="Soyad" error={errors.lastName?.message} required>
              <input
                {...register('lastName', { required: 'Soyad tələb olunur' })}
                autoComplete="family-name"
                className={inputClass(Boolean(errors.lastName))}
              />
            </Field>
          </div>

          <Field label="E-poçt" error={errors.email?.message} required>
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

          <Field label="Telefon (istəyə bağlı)" error={errors.phone?.message}>
            <input
              {...register('phone')}
              type="tel"
              autoComplete="tel"
              placeholder="+994 50 000 00 00"
              className={inputClass(Boolean(errors.phone))}
            />
          </Field>

          <Field label="Şifrə" error={errors.password?.message} required>
            <input
              {...register('password', {
                required: 'Şifrə tələb olunur',
                minLength: { value: 10, message: 'Şifrə ən azı 10 simvol olmalıdır' },
              })}
              type="password"
              autoComplete="new-password"
              className={inputClass(Boolean(errors.password))}
            />
          </Field>

          <label className="flex cursor-pointer items-start gap-2.5 text-sm">
            <input
              type="checkbox"
              {...register('marketingOptIn')}
              className="mt-0.5 h-4 w-4 rounded accent-[var(--color-primary)]"
            />
            <span className="text-[var(--color-body)]">Endirim və kampaniyalardan xəbərdar olmaq istəyirəm</span>
          </label>

          <div>
            <label className="flex cursor-pointer items-start gap-2.5 text-sm">
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
              <p className="mt-1 text-xs text-[var(--color-danger)]">{errors.termsAccepted.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
          >
            <UserPlus size={16} aria-hidden="true" />
            Hesab yarat
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--color-body)]">
          Artıq hesabınız var?{' '}
          <Link
            to={`/hesab/giris${next !== '/hesab/profil' ? `?next=${encodeURIComponent(next)}` : ''}`}
            className="font-medium text-[var(--color-primary)] hover:underline"
          >
            Daxil olun
          </Link>
        </p>
      </div>
    </div>
  );
}
