import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Lock, ShieldCheck } from 'lucide-react';
import { Button, Field, Input } from '../components/ui';
import { useAdminAuth } from '../auth/adminAuth';
import { ApiError } from '@/lib/api';

interface LoginForm {
  email: string;
  password: string;
}

export function AdminLoginPage() {
  const login = useAdminAuth((state) => state.login);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>();

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values.email, values.password);
    } catch (error) {
      // Credential errors are shown verbatim; the server deliberately does not say
      // whether it was the address or the password that was wrong.
      setFormError(
        error instanceof ApiError ? error.message : 'Giriş alınmadı. Yenidən cəhd edin.',
      );
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-muted)] px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-[var(--radius-card)] bg-[var(--color-primary)]">
            <ShieldCheck size={22} className="text-[var(--color-primary-contrast)]" aria-hidden="true" />
          </div>
          <h1 className="text-xl font-bold text-[var(--color-heading)]">Admin panel</h1>
          <p className="mt-1 text-sm text-[var(--color-body)]">Davam etmək üçün daxil olun</p>
        </div>

        <form
          onSubmit={onSubmit}
          noValidate
          className="rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6"
        >
          {formError && (
            <p
              role="alert"
              className="mb-4 rounded-[var(--radius-button)] bg-[var(--color-danger)]/10 px-3 py-2.5 text-sm text-[var(--color-danger)]"
            >
              {formError}
            </p>
          )}

          <div className="space-y-4">
            <Field label="E-poçt" htmlFor="email" error={errors.email?.message} required>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                autoFocus
                hasError={Boolean(errors.email)}
                {...register('email', {
                  required: 'E-poçt tələb olunur',
                  pattern: {
                    value: /^[^@\s]+@[^@\s]+\.[^@\s]+$/,
                    message: 'E-poçt formatı yanlışdır',
                  },
                })}
              />
            </Field>

            <Field label="Şifrə" htmlFor="password" error={errors.password?.message} required>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                hasError={Boolean(errors.password)}
                {...register('password', { required: 'Şifrə tələb olunur' })}
              />
            </Field>
          </div>

          <Button
            type="submit"
            loading={isSubmitting}
            icon={<Lock size={15} aria-hidden="true" />}
            className="mt-6 w-full"
            size="lg"
          >
            Daxil ol
          </Button>
        </form>

        <p className="mt-4 text-center text-xs text-[var(--color-subtle)]">
          Şifrənizi unutmusunuz? Sistem administratoru ilə əlaqə saxlayın.
        </p>
      </div>
    </div>
  );
}
