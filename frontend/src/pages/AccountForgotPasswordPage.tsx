import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { MailCheck, Send } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { apiClient } from '@/lib/api';

interface ForgotPasswordValues {
  email: string;
}

/**
 * Always ends in the same "check your inbox" screen regardless of whether the address is
 * registered — the backend reports success unconditionally for the same reason, so a
 * different outcome here would leak what the API deliberately hides.
 */
export default function AccountForgotPasswordPage() {
  const [sent, setSent] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordValues>();

  const mutation = useMutation({
    mutationFn: (values: ForgotPasswordValues) =>
      apiClient.post('/customer/auth/forgot-password', values),
    onSuccess: () => setSent(true),
    onError: () => setSent(true),
  });

  const onSubmit = handleSubmit((values) => mutation.mutate(values));

  return (
    <div className="container-site flex min-h-[70vh] items-center justify-center py-12">
      <div className="card w-full max-w-md p-6 sm:p-8">
        {sent ? (
          <div className="text-center">
            <MailCheck size={32} className="mx-auto mb-3 text-[var(--color-primary)]" aria-hidden="true" />
            <h1 className="mb-2 text-xl font-bold tracking-tight">E-poçtunuzu yoxlayın</h1>
            <p className="text-sm text-[var(--color-body)]">
              Əgər bu ünvanla hesab mövcuddursa, şifrə bərpası üçün link göndərildi.
            </p>
            <Link
              to="/hesab/giris"
              className="mt-6 inline-block text-sm font-medium text-[var(--color-primary)] hover:underline"
            >
              Girişə qayıt
            </Link>
          </div>
        ) : (
          <>
            <h1 className="mb-1 text-2xl font-bold tracking-tight">Şifrəni unutmusunuz?</h1>
            <p className="mb-6 text-sm text-[var(--color-subtle)]">
              E-poçt ünvanınızı yazın, bərpa linki göndərək.
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

              <button
                type="submit"
                disabled={mutation.isPending}
                className="flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
              >
                <Send size={16} aria-hidden="true" />
                Bərpa linki göndər
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-[var(--color-body)]">
              <Link to="/hesab/giris" className="font-medium text-[var(--color-primary)] hover:underline">
                Girişə qayıt
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
