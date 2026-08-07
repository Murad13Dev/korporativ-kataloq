import { Link, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle2, KeyRound, ShieldAlert } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { ApiError, apiClient } from '@/lib/api';

interface ResetPasswordValues {
  password: string;
  confirmPassword: string;
}

export default function AccountResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ResetPasswordValues>();

  const mutation = useMutation({
    mutationFn: (password: string) =>
      apiClient.post('/customer/auth/reset-password', { token, password }),
  });

  const onSubmit = handleSubmit((values) => {
    if (values.password !== values.confirmPassword) {
      setError('confirmPassword', { message: 'Şifrələr uyğun gəlmir' });
      return;
    }
    mutation.mutate(values.password);
  });

  if (!token) {
    return <InvalidLinkScreen />;
  }

  if (mutation.isError && mutation.error instanceof ApiError && mutation.error.is('TOKEN_INVALID', 'TOKEN_EXPIRED')) {
    return <InvalidLinkScreen />;
  }

  if (mutation.isSuccess) {
    return (
      <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
        <div className="card w-full max-w-md p-6 text-center sm:p-8">
          <CheckCircle2 size={32} className="mx-auto mb-3 text-[var(--color-success)]" aria-hidden="true" />
          <h1 className="mb-2 text-xl font-bold tracking-tight">Şifrə yeniləndi</h1>
          <p className="mb-6 text-sm text-[var(--color-body)]">
            Yeni şifrənizlə daxil ola bilərsiniz.
          </p>
          <Link
            to="/hesab/giris"
            className="inline-flex items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
          >
            Daxil ol
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
      <div className="card w-full max-w-md p-6 sm:p-8">
        <h1 className="mb-1 text-2xl font-bold tracking-tight">Yeni şifrə təyin edin</h1>
        <p className="mb-6 text-sm text-[var(--color-subtle)]">Hesabınız üçün yeni şifrə seçin.</p>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <Field label="Yeni şifrə" error={errors.password?.message} required>
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

          <Field label="Yeni şifrə (təkrar)" error={errors.confirmPassword?.message} required>
            <input
              {...register('confirmPassword', { required: 'Şifrəni təkrarlayın' })}
              type="password"
              autoComplete="new-password"
              className={inputClass(Boolean(errors.confirmPassword))}
            />
          </Field>

          <button
            type="submit"
            disabled={mutation.isPending}
            className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
          >
            <KeyRound size={16} aria-hidden="true" />
            Şifrəni yenilə
          </button>
        </form>
      </div>
    </div>
  );
}

function InvalidLinkScreen() {
  return (
    <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
      <div className="card w-full max-w-md p-6 text-center sm:p-8">
        <ShieldAlert size={32} className="mx-auto mb-3 text-[var(--color-danger)]" aria-hidden="true" />
        <h1 className="mb-2 text-xl font-bold tracking-tight">Link etibarsızdır</h1>
        <p className="mb-6 text-sm text-[var(--color-body)]">
          Bu link artıq istifadə olunub və ya vaxtı bitib. Yenidən sorğu göndərin.
        </p>
        <Link
          to="/hesab/sifreni-unutdum"
          className="inline-flex items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
        >
          Yeni link tələb et
        </Link>
      </div>
    </div>
  );
}
