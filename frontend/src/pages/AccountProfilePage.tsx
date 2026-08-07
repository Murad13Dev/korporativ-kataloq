import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { KeyRound, Save } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { useToast } from '@/components/ui/Toast';
import { useCustomerAuth, type UpdateProfilePayload } from '@/hooks/useCustomerAuth';
import { apiClient, ApiError } from '@/lib/api';

export default function AccountProfilePage() {
  return (
    <div className="space-y-6">
      <ProfileForm />
      <PasswordForm />
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
