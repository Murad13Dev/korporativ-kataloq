import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Building2, Mail, ShieldCheck, Trash2, UserPlus } from 'lucide-react';
import { SectionLoader } from '@/components/ui/PageLoader';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData } from '@/lib/api';

type CompanyStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED';
type CompanyRole = 'OWNER' | 'BUYER';

interface Member {
  id: number;
  customerId: number;
  name: string;
  email: string;
  role: CompanyRole;
  createdAt: string;
}

interface CompanyView {
  id: number;
  name: string;
  taxNumber: string | null;
  discountPercentage: number;
  status: CompanyStatus;
  myRole: CompanyRole;
  members: Member[];
}

const STATUS_LABEL: Record<CompanyStatus, string> = {
  PENDING: 'Təsdiq gözləyir',
  ACTIVE: 'Aktiv',
  SUSPENDED: 'Dayandırılıb',
};

const STATUS_TONE: Record<CompanyStatus, string> = {
  PENDING: 'bg-[var(--color-warning)]/12 text-[var(--color-warning)]',
  ACTIVE: 'bg-[var(--color-success)]/12 text-[var(--color-success)]',
  SUSPENDED: 'bg-[var(--color-danger)]/12 text-[var(--color-danger)]',
};

/**
 * A customer's B2B account: either the create-company prompt, or the company itself
 * with its member roster. Only the owner sees invite/remove controls — everyone else
 * in the company just sees who else is on it.
 */
export default function AccountCompanyPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const company = useQuery({
    queryKey: ['customer', 'company'],
    queryFn: () => getData<CompanyView | null>('/customer/company'),
  });

  const invite = useInviteMutation(() => {
    queryClient.invalidateQueries({ queryKey: ['customer', 'company'] });
  });
  const remove = useMutation({
    mutationFn: (memberId: number) => deleteRequest(`/customer/company/members/${memberId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', 'company'] });
      toast.success('Üzv çıxarıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (company.isLoading) {
    return <SectionLoader label="Yüklənir…" />;
  }

  if (!company.data) {
    return <CreateCompanyForm onCreated={() => queryClient.invalidateQueries({ queryKey: ['customer', 'company'] })} />;
  }

  const view = company.data;
  const isOwner = view.myRole === 'OWNER';

  return (
    <div className="space-y-5">
      <div className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]">
              <Building2 size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-lg font-bold text-[var(--color-heading)]">{view.name}</h2>
              {view.taxNumber && <p className="text-xs text-[var(--color-subtle)]">VÖEN: {view.taxNumber}</p>}
            </div>
          </div>
          <span className={clsx('rounded-full px-3 py-1 text-xs font-medium', STATUS_TONE[view.status])}>
            {STATUS_LABEL[view.status]}
          </span>
        </div>

        {view.status === 'PENDING' && (
          <p className="mt-4 rounded-[var(--radius-button)] bg-[var(--color-warning)]/8 p-3 text-xs text-[var(--color-body)]">
            Şirkət hesabınız yoxlanılır. Təsdiqləndikdən sonra razılaşdırılmış endirim sifarişlərinizə
            avtomatik tətbiq olunacaq.
          </p>
        )}

        {view.status === 'ACTIVE' && view.discountPercentage > 0 && (
          <p className="mt-4 flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-success)]/8 p-3 text-sm text-[var(--color-success)]">
            <ShieldCheck size={16} aria-hidden="true" />
            Sifarişlərinizə {view.discountPercentage}% korporativ endirim tətbiq olunur
          </p>
        )}
      </div>

      <div className="card p-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-sm font-bold uppercase tracking-wide text-[var(--color-subtle)]">
            Üzvlər ({view.members.length})
          </h3>
        </div>

        <ul className="space-y-2">
          {view.members.map((member) => (
            <li
              key={member.id}
              className="flex items-center justify-between gap-3 rounded-[var(--radius-button)] border border-[var(--color-border)] px-3 py-2.5"
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-[var(--color-heading)]">{member.name}</p>
                <p className="truncate text-xs text-[var(--color-subtle)]">{member.email}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span
                  className={clsx(
                    'rounded-full px-2 py-0.5 text-[0.6875rem] font-medium',
                    member.role === 'OWNER'
                      ? 'bg-[var(--color-primary)]/12 text-[var(--color-primary)]'
                      : 'bg-[var(--color-muted)] text-[var(--color-subtle)]',
                  )}
                >
                  {member.role === 'OWNER' ? 'Sahib' : 'Alıcı'}
                </span>
                {isOwner && member.role !== 'OWNER' && (
                  <button
                    type="button"
                    onClick={() => remove.mutate(member.id)}
                    aria-label={`${member.name} üzvünü çıxar`}
                    className="rounded p-1 text-[var(--color-subtle)] hover:bg-[var(--color-muted)] hover:text-[var(--color-danger)]"
                  >
                    <Trash2 size={14} aria-hidden="true" />
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>

        {isOwner && (
          <form
            className="mt-4 flex gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              const email = new FormData(event.currentTarget).get('email') as string;
              if (email.trim()) {
                invite.mutate(email.trim(), { onSuccess: () => event.currentTarget.reset() });
              }
            }}
          >
            <label htmlFor="invite-email" className="sr-only">
              Üzv e-poçtu
            </label>
            <div className="relative flex-1">
              <Mail size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]" aria-hidden="true" />
              <input
                id="invite-email"
                name="email"
                type="email"
                required
                placeholder="kimin@e-poçtu.az"
                className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] py-2 ps-9 pe-3 text-sm outline-none focus:border-[var(--color-primary)]"
              />
            </div>
            <button
              type="submit"
              disabled={invite.isPending}
              className="flex items-center gap-1.5 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-[var(--color-primary-contrast)] disabled:opacity-50"
            >
              <UserPlus size={15} aria-hidden="true" />
              Dəvət et
            </button>
          </form>
        )}
        {isOwner && (
          <p className="mt-2 text-xs text-[var(--color-subtle)]">
            Dəvət olunan şəxs əvvəlcədən saytda qeydiyyatdan keçmiş olmalıdır.
          </p>
        )}
      </div>
    </div>
  );
}

function useInviteMutation(onDone: () => void) {
  const toast = useToast();
  return useMutation({
    mutationFn: (email: string) => postData('/customer/company/members', { email }),
    onSuccess: () => {
      onDone();
      toast.success('Üzv əlavə edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });
}

function CreateCompanyForm({ onCreated }: { onCreated: () => void }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [taxNumber, setTaxNumber] = useState('');

  const create = useMutation({
    mutationFn: () => postData('/customer/company', { name, taxNumber: taxNumber || undefined }),
    onSuccess: () => {
      onCreated();
      toast.success('Şirkət hesabı yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <div className="card mx-auto max-w-md p-6 text-center">
      <Building2 size={28} className="mx-auto mb-3 text-[var(--color-primary)]" aria-hidden="true" />
      <h2 className="text-lg font-bold text-[var(--color-heading)]">Şirkət hesabı yaradın</h2>
      <p className="mt-1.5 text-sm text-[var(--color-body)]">
        Komandanızla birgə sifariş verin və razılaşdırılmış qiymətlərdən faydalanın.
      </p>

      <form
        className="mt-5 space-y-3 text-start"
        onSubmit={(event) => {
          event.preventDefault();
          if (name.trim()) {
            create.mutate();
          }
        }}
      >
        <div>
          <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">Şirkət adı</label>
          <input
            required
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-[var(--color-heading)]">VÖEN (istəyə bağlı)</label>
          <input
            value={taxNumber}
            onChange={(event) => setTaxNumber(event.target.value)}
            className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2 text-sm outline-none focus:border-[var(--color-primary)]"
          />
        </div>
        <button
          type="submit"
          disabled={create.isPending || !name.trim()}
          className="w-full rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] disabled:opacity-50"
        >
          {create.isPending ? 'Yaradılır…' : 'Şirkət hesabı yarat'}
        </button>
      </form>
    </div>
  );
}
