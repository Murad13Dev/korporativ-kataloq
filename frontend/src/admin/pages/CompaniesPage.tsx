import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Search, ShieldCheck, Users } from 'lucide-react';
import { Badge, type BadgeTone, Button, Card, EmptyState, Input, PageHeader, Select, TableSkeleton } from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, getPage, patchData } from '@/lib/api';
import { formatDateTime } from '@/lib/format';

type CompanyStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED';

interface CompanyRow {
  id: number;
  name: string;
  taxNumber: string | null;
  discountPercentage: number;
  status: CompanyStatus;
  memberCount: number;
  createdAt: string;
}

interface Member {
  id: number;
  name: string;
  email: string;
  role: 'OWNER' | 'BUYER';
}

interface CompanyDetail extends CompanyRow {
  members: Member[];
}

const STATUS_LABEL: Record<CompanyStatus, string> = {
  PENDING: 'Təsdiq gözləyir',
  ACTIVE: 'Aktiv',
  SUSPENDED: 'Dayandırılıb',
};

const STATUS_TONE: Record<CompanyStatus, BadgeTone> = {
  PENDING: 'warning',
  ACTIVE: 'success',
  SUSPENDED: 'danger',
};

/**
 * B2B company accounts. A company sits PENDING until an admin approves it here — its
 * negotiated discount is inert until then, so this list doubles as the vetting queue.
 */
export function CompaniesPage() {
  const [search, setSearch] = useState('');
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const companies = useQuery({
    queryKey: ['admin', 'companies', search],
    queryFn: () => getPage<CompanyRow>('/admin/companies', { search: search || undefined, size: 50 }),
  });

  const rows = companies.data;

  return (
    <>
      <PageHeader title="Şirkətlər" description="B2B hesablar, üzvlər və razılaşdırılmış endirimlər" />

      <div className="mb-4 relative max-w-sm">
        <Search size={15} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]" aria-hidden="true" />
        <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Ad və ya VÖEN axtar…" className="ps-9" />
      </div>

      <Card bodyClassName="p-0">
        {companies.isLoading ? (
          <TableSkeleton rows={5} columns={4} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState icon={<Building2 size={32} aria-hidden="true" />} title="Şirkət yoxdur" />
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {rows.content.map((company) => (
              <li key={company.id}>
                <button
                  type="button"
                  onClick={() => setExpandedId(expandedId === company.id ? null : company.id)}
                  className="flex w-full flex-wrap items-center justify-between gap-3 px-5 py-4 text-start hover:bg-[var(--color-muted)]"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-[var(--color-heading)]">{company.name}</span>
                      <Badge tone={STATUS_TONE[company.status]}>{STATUS_LABEL[company.status]}</Badge>
                    </div>
                    <p className="mt-0.5 flex items-center gap-3 text-xs text-[var(--color-subtle)]">
                      {company.taxNumber && <span>VÖEN: {company.taxNumber}</span>}
                      <span className="flex items-center gap-1">
                        <Users size={12} aria-hidden="true" /> {company.memberCount}
                      </span>
                      <span>{formatDateTime(company.createdAt)}</span>
                    </p>
                  </div>
                  <div className="flex items-center gap-1.5 text-sm font-semibold text-[var(--color-heading)]">
                    <ShieldCheck size={14} className="text-[var(--color-primary)]" aria-hidden="true" />
                    {company.discountPercentage}%
                  </div>
                </button>

                {expandedId === company.id && <CompanyPanel id={company.id} />}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}

function CompanyPanel({ id }: { id: number }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const detail = useQuery({
    queryKey: ['admin', 'companies', id],
    queryFn: () => getData<CompanyDetail>(`/admin/companies/${id}`),
  });

  const [discount, setDiscount] = useState<string>('');

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'companies'] });
  };

  const setStatus = useMutation({
    mutationFn: (status: CompanyStatus) => patchData(`/admin/companies/${id}/status`, { status }),
    onSuccess: () => {
      invalidate();
      toast.success('Status yeniləndi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const setDiscountMutation = useMutation({
    mutationFn: (value: number) => patchData(`/admin/companies/${id}/discount`, { discountPercentage: value }),
    onSuccess: () => {
      invalidate();
      toast.success('Endirim yeniləndi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  if (detail.isLoading || !detail.data) {
    return <div className="border-t border-[var(--color-border)] px-5 py-4"><TableSkeleton rows={2} columns={1} /></div>;
  }

  const company = detail.data;

  return (
    <div className="space-y-4 border-t border-[var(--color-border)] bg-[var(--color-muted)]/40 px-5 py-4">
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-[var(--color-heading)]">Status</label>
          <Select
            value={company.status}
            onChange={(event) => setStatus.mutate(event.target.value as CompanyStatus)}
            disabled={setStatus.isPending}
            className="w-40"
          >
            <option value="PENDING">Təsdiq gözləyir</option>
            <option value="ACTIVE">Aktiv</option>
            <option value="SUSPENDED">Dayandırılıb</option>
          </Select>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-[var(--color-heading)]">Endirim (%)</label>
          <div className="flex gap-2">
            <Input
              type="number"
              min={0}
              max={100}
              step={0.5}
              defaultValue={company.discountPercentage}
              onChange={(event) => setDiscount(event.target.value)}
              className="w-24"
            />
            <Button
              size="sm"
              variant="secondary"
              loading={setDiscountMutation.isPending}
              onClick={() => {
                const value = Number(discount);
                if (!Number.isNaN(value)) {
                  setDiscountMutation.mutate(value);
                }
              }}
            >
              Tətbiq et
            </Button>
          </div>
        </div>
      </div>

      <div>
        <h4 className="mb-2 text-xs font-bold uppercase tracking-wide text-[var(--color-subtle)]">
          Üzvlər ({company.members.length})
        </h4>
        <ul className="space-y-1.5">
          {company.members.map((member) => (
            <li key={member.id} className="flex items-center justify-between text-sm">
              <span className="text-[var(--color-body)]">
                {member.name} <span className="text-xs text-[var(--color-subtle)]">({member.email})</span>
              </span>
              <Badge tone={member.role === 'OWNER' ? 'info' : 'neutral'}>
                {member.role === 'OWNER' ? 'Sahib' : 'Alıcı'}
              </Badge>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
