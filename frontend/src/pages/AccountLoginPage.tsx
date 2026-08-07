import { useEffect } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQueryClient } from '@tanstack/react-query';
import { LogIn } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { useCustomerAuth, type LoginPayload } from '@/hooks/useCustomerAuth';
import { cartQueryKey } from '@/hooks/useCart';
import { ApiError } from '@/lib/api';

export default function AccountLoginPage() {
  const { status, restore, login } = useCustomerAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const next = searchParams.get('next') ?? '/hesab/profil';

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
  } = useForm<LoginPayload>();

  if (status === 'idle' || status === 'checking') {
    return <SectionLoader label="Yoxlanılır…" />;
  }

  // Already signed in — the login form itself would be a dead end.
  if (status === 'authenticated') {
    return <Navigate to={next} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values);
      // The cart just merged server-side; the header badge and cart page need the
      // fresh totals rather than the stale guest-cart snapshot they had cached.
      await queryClient.invalidateQueries({ queryKey: cartQueryKey });
      navigate(next, { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.is('INVALID_CREDENTIALS')) {
          setError('password', { message: 'E-poçt və ya şifrə yanlışdır' });
        } else {
          toast.error(error.message);
        }
      }
    }
  });

  return (
    <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
      <div className="card w-full max-w-md p-6 sm:p-8">
        <h1 className="mb-1 text-2xl font-bold tracking-tight">Hesaba giriş</h1>
        <p className="mb-6 text-sm text-[var(--color-subtle)]">
          Sifariş tarixçənizi və ünvanlarınızı idarə edin.
        </p>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
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

          <div>
            <Field label="Şifrə" error={errors.password?.message} required>
              <input
                {...register('password', { required: 'Şifrə tələb olunur' })}
                type="password"
                autoComplete="current-password"
                className={inputClass(Boolean(errors.password))}
              />
            </Field>
            <Link
              to="/hesab/sifreni-unutdum"
              className="mt-1.5 inline-block text-xs font-medium text-[var(--color-primary)] hover:underline"
            >
              Şifrəni unutmusunuz?
            </Link>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
          >
            <LogIn size={16} aria-hidden="true" />
            Daxil ol
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--color-body)]">
          Hesabınız yoxdur?{' '}
          <Link
            to={`/hesab/qeydiyyat${next !== '/hesab/profil' ? `?next=${encodeURIComponent(next)}` : ''}`}
            className="font-medium text-[var(--color-primary)] hover:underline"
          >
            Qeydiyyatdan keçin
          </Link>
        </p>
        <p className="mt-2 text-center text-sm text-[var(--color-body)]">
          Yalnız sifarişinizi izləmək istəyirsiniz?{' '}
          <Link to="/sifarisi-izle" className="font-medium text-[var(--color-primary)] hover:underline">
            Buradan izləyin
          </Link>
        </p>
      </div>
    </div>
  );
}
