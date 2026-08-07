import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MapPin, Pencil, Plus, Star, Trash2, X } from 'lucide-react';
import { Field, inputClass } from '@/components/ui/FormField';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, patchData, postData, putData } from '@/lib/api';
import type { Address } from '@/types/api';

const ADDRESSES_KEY = ['customer', 'addresses'] as const;

type AddressFormValues = {
  title: string;
  fullName?: string;
  phone?: string;
  country?: string;
  city: string;
  district?: string;
  addressLine: string;
  postalCode?: string;
  notes?: string;
  isDefault?: boolean;
};

export default function AccountAddressesPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [editingId, setEditingId] = useState<number | 'new' | null>(null);

  const { data: addresses, isLoading } = useQuery({
    queryKey: ADDRESSES_KEY,
    queryFn: () => getData<Address[]>('/customer/addresses'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY });

  const createMutation = useMutation({
    mutationFn: (values: AddressFormValues) => postData<Address>('/customer/addresses', values),
    onSuccess: () => {
      toast.success('Ünvan əlavə edildi');
      setEditingId(null);
      void invalidate();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: AddressFormValues }) =>
      putData<Address>(`/customer/addresses/${id}`, values),
    onSuccess: () => {
      toast.success('Ünvan yeniləndi');
      setEditingId(null);
      void invalidate();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteRequest(`/customer/addresses/${id}`),
    onSuccess: () => {
      toast.success('Ünvan silindi');
      void invalidate();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const defaultMutation = useMutation({
    mutationFn: (id: number) => patchData<Address>(`/customer/addresses/${id}/default`, {}),
    onSuccess: () => void invalidate(),
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (isLoading) {
    return <SectionLoader label="Ünvanlar yüklənir…" />;
  }

  const list = addresses ?? [];

  return (
    <div className="card p-5 sm:p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold">Ünvanlarım</h2>
        {editingId === null && (
          <button
            type="button"
            onClick={() => setEditingId('new')}
            className="flex items-center gap-1.5 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
          >
            <Plus size={16} aria-hidden="true" />
            Yeni ünvan
          </button>
        )}
      </div>

      {editingId === 'new' && (
        <AddressForm
          onCancel={() => setEditingId(null)}
          onSubmit={(values) => createMutation.mutate(values)}
          isPending={createMutation.isPending}
        />
      )}

      {list.length === 0 && editingId === null && (
        <div className="flex flex-col items-center gap-2 py-12 text-center text-[var(--color-subtle)]">
          <MapPin size={28} aria-hidden="true" />
          <p className="text-sm">Hələ ünvan əlavə etməmisiniz</p>
        </div>
      )}

      <ul className="space-y-3">
        {list.map((address) =>
          editingId === address.id ? (
            <li key={address.id}>
              <AddressForm
                initial={address}
                onCancel={() => setEditingId(null)}
                onSubmit={(values) => updateMutation.mutate({ id: address.id, values })}
                isPending={updateMutation.isPending}
              />
            </li>
          ) : (
            <li
              key={address.id}
              className="flex flex-col gap-3 rounded-[var(--radius-card)] border border-[var(--color-border)] p-4 sm:flex-row sm:items-start sm:justify-between"
            >
              <div className="min-w-0">
                <div className="mb-1 flex items-center gap-2">
                  <span className="font-semibold text-[var(--color-heading)]">{address.title}</span>
                  {address.isDefault && (
                    <span className="rounded-full bg-[var(--color-primary)]/10 px-2 py-0.5 text-[0.6875rem] font-semibold text-[var(--color-primary)]">
                      Əsas
                    </span>
                  )}
                </div>
                {address.fullName && (
                  <p className="text-sm text-[var(--color-body)]">{address.fullName}</p>
                )}
                <p className="text-sm text-[var(--color-body)]">
                  {[address.addressLine, address.district, address.city, address.country]
                    .filter(Boolean)
                    .join(', ')}
                </p>
                {address.phone && <p className="text-sm text-[var(--color-subtle)]">{address.phone}</p>}
              </div>

              <div className="flex shrink-0 items-center gap-1">
                {!address.isDefault && (
                  <button
                    type="button"
                    onClick={() => defaultMutation.mutate(address.id)}
                    title="Əsas ünvan et"
                    className="rounded-[var(--radius-button)] p-2 text-[var(--color-subtle)] transition-colors hover:bg-[var(--color-muted)] hover:text-[var(--color-primary)]"
                  >
                    <Star size={16} aria-hidden="true" />
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setEditingId(address.id)}
                  title="Redaktə et"
                  className="rounded-[var(--radius-button)] p-2 text-[var(--color-subtle)] transition-colors hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]"
                >
                  <Pencil size={16} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (window.confirm('Bu ünvanı silmək istədiyinizə əminsiniz?')) {
                      deleteMutation.mutate(address.id);
                    }
                  }}
                  title="Sil"
                  className="rounded-[var(--radius-button)] p-2 text-[var(--color-subtle)] transition-colors hover:bg-[var(--color-danger)]/10 hover:text-[var(--color-danger)]"
                >
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </div>
            </li>
          ),
        )}
      </ul>
    </div>
  );
}

function AddressForm({
  initial,
  onSubmit,
  onCancel,
  isPending,
}: {
  initial?: Address;
  onSubmit: (values: AddressFormValues) => void;
  onCancel: () => void;
  isPending: boolean;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AddressFormValues>({
    defaultValues: initial
      ? {
          title: initial.title,
          fullName: initial.fullName ?? '',
          phone: initial.phone ?? '',
          country: initial.country,
          city: initial.city,
          district: initial.district ?? '',
          addressLine: initial.addressLine,
          postalCode: initial.postalCode ?? '',
          notes: initial.notes ?? '',
          isDefault: initial.isDefault,
        }
      : { country: 'Azərbaycan' },
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="mb-4 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4"
      noValidate
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Ünvan başlığı (məs. Ev, Ofis)" error={errors.title?.message} required>
          <input
            {...register('title', { required: 'Başlıq tələb olunur' })}
            className={inputClass(Boolean(errors.title))}
          />
        </Field>
        <Field label="Əlaqədar şəxs">
          <input {...register('fullName')} className={inputClass(false)} />
        </Field>
        <Field label="Telefon">
          <input {...register('phone')} type="tel" className={inputClass(false)} />
        </Field>
        <Field label="Şəhər" error={errors.city?.message} required>
          <input
            {...register('city', { required: 'Şəhər tələb olunur' })}
            className={inputClass(Boolean(errors.city))}
          />
        </Field>
        <Field label="Rayon">
          <input {...register('district')} className={inputClass(false)} />
        </Field>
        <Field label="Poçt kodu">
          <input {...register('postalCode')} className={inputClass(false)} />
        </Field>
        <Field label="Ünvan" error={errors.addressLine?.message} required className="sm:col-span-2">
          <textarea
            {...register('addressLine', { required: 'Ünvan tələb olunur' })}
            rows={2}
            className={inputClass(Boolean(errors.addressLine))}
          />
        </Field>
        <Field label="Qeyd" className="sm:col-span-2">
          <textarea {...register('notes')} rows={2} className={inputClass(false)} />
        </Field>
      </div>

      <label className="mt-3 flex cursor-pointer items-center gap-2.5 text-sm">
        <input
          type="checkbox"
          {...register('isDefault')}
          className="h-4 w-4 rounded accent-[var(--color-primary)]"
        />
        <span className="text-[var(--color-body)]">Əsas ünvan kimi təyin et</span>
      </label>

      <div className="mt-4 flex gap-2">
        <button
          type="submit"
          disabled={isPending}
          className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-50"
        >
          Yadda saxla
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="flex items-center gap-1.5 rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2.5 text-sm font-medium text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)]"
        >
          <X size={15} aria-hidden="true" />
          Ləğv et
        </button>
      </div>
    </form>
  );
}
