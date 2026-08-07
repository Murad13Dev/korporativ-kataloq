import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  Building2,
  Inbox,
  Mail,
  MapPin,
  Package,
  Phone,
  Save,
  ShieldAlert,
  Trash2,
} from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  InlineLoader,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  Textarea,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { useCan } from '../auth/adminAuth';
import { ApiError, deleteRequest, getData, getPage, patchData, putData } from '@/lib/api';
import { formatDateTime, formatNumber, formatRelative } from '@/lib/format';

type Status = 'NEW' | 'IN_PROGRESS' | 'ANSWERED' | 'CLOSED' | 'SPAM';
type RequestType = 'CONTACT' | 'PRODUCT_INQUIRY' | 'QUOTE' | 'SUPPORT' | 'PARTNERSHIP';

interface RequestRow {
  id: number;
  name: string;
  companyName: string | null;
  email: string;
  phone: string | null;
  subject: string | null;
  requestType: RequestType;
  requestTypeLabel: string;
  status: Status;
  statusLabel: string;
  productId: number | null;
  productName: string | null;
  createdAt: string;
}

interface RequestDetail extends RequestRow {
  message: string;
  productSlug: string | null;
  adminNote: string | null;
  handledBy: number | null;
  handledByName: string | null;
  handledAt: string | null;
  sourceUrl: string | null;
  ipAddress: string | null;
}

interface Counters {
  total: number;
  newCount: number;
  inProgress: number;
  answered: number;
  spam: number;
}

const STATUS_TONE: Record<Status, BadgeTone> = {
  NEW: 'info',
  IN_PROGRESS: 'warning',
  ANSWERED: 'success',
  CLOSED: 'neutral',
  SPAM: 'danger',
};

const STATUS_OPTIONS: { value: Status; label: string }[] = [
  { value: 'NEW', label: 'Yeni' },
  { value: 'IN_PROGRESS', label: 'İşlənir' },
  { value: 'ANSWERED', label: 'Cavablandırılıb' },
  { value: 'CLOSED', label: 'Bağlanıb' },
  { value: 'SPAM', label: 'Spam' },
];

/**
 * Lead inbox.
 *
 * Two panes, like the chat console: the list on the left never loses its place while an
 * operator reads and answers on the right. Replying happens in the operator's own mail
 * client — there is no send-from-here, because a reply that does not appear in the
 * sender's sent folder is a reply they cannot follow up on.
 */
export function ContactRequestsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const canDelete = useCan('orders.manage');

  // Dashboard alerts deep-link here with ?status=NEW — the chips take over from there,
  // so the URL only needs to seed the initial filter, not stay in sync afterwards.
  const [searchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [status, setStatus] = useState<string>(() => searchParams.get('status') ?? '');
  const [type, setType] = useState<string>('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [noteDraft, setNoteDraft] = useState<string | null>(null);

  const requests = useQuery({
    queryKey: ['admin', 'contact-requests', { status, type, search, page }],
    queryFn: () =>
      getPage<RequestRow>('/admin/contact-requests', {
        status: status || undefined,
        type: type || undefined,
        search: search || undefined,
        page,
        size: 25,
      }),
    placeholderData: (previous) => previous,
    // Leads arrive while the operator is looking at the list.
    refetchInterval: 60_000,
  });

  const counters = useQuery({
    queryKey: ['admin', 'contact-requests', 'counters'],
    queryFn: () => getData<Counters>('/admin/contact-requests/counters'),
    refetchInterval: 60_000,
  });

  const detail = useQuery({
    queryKey: ['admin', 'contact-request', selectedId],
    queryFn: () => getData<RequestDetail>(`/admin/contact-requests/${selectedId}`),
    enabled: selectedId !== null,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'contact-requests'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
  };

  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: number; next: Status }) =>
      patchData<RequestDetail>(`/admin/contact-requests/${id}/status`, { status: next }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['admin', 'contact-request', updated.id], updated);
      refresh();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const saveNote = useMutation({
    mutationFn: ({ id, note }: { id: number; note: string }) =>
      putData<RequestDetail>(`/admin/contact-requests/${id}/note`, { note }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['admin', 'contact-request', updated.id], updated);
      setNoteDraft(null);
      toast.success('Qeyd yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/contact-requests/${id}`),
    onSuccess: () => {
      setSelectedId(null);
      refresh();
      toast.success('Sorğu silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const rows = requests.data;
  const current = detail.data;

  return (
    <>
      <PageHeader
        title="Əlaqə sorğuları"
        description="Saytdakı formalardan gələn müraciətlər"
      />

      {/* Status chips double as filters and as the only counter an operator needs. */}
      {counters.data && (
        <div className="mb-6 flex flex-wrap gap-2">
          <FilterChip
            label="Hamısı"
            count={counters.data.total}
            active={!status}
            onClick={() => {
              setStatus('');
              setPage(0);
            }}
          />
          <FilterChip
            label="Yeni"
            count={counters.data.newCount}
            tone="info"
            active={status === 'NEW'}
            onClick={() => {
              setStatus('NEW');
              setPage(0);
            }}
          />
          <FilterChip
            label="İşlənir"
            count={counters.data.inProgress}
            tone="warning"
            active={status === 'IN_PROGRESS'}
            onClick={() => {
              setStatus('IN_PROGRESS');
              setPage(0);
            }}
          />
          <FilterChip
            label="Cavablandırılıb"
            count={counters.data.answered}
            tone="success"
            active={status === 'ANSWERED'}
            onClick={() => {
              setStatus('ANSWERED');
              setPage(0);
            }}
          />
          {counters.data.spam > 0 && (
            <FilterChip
              label="Spam"
              count={counters.data.spam}
              tone="danger"
              active={status === 'SPAM'}
              onClick={() => {
                setStatus('SPAM');
                setPage(0);
              }}
            />
          )}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_420px]">
        {/* ---------------- list ---------------- */}
        <Card bodyClassName="p-0">
          <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Ad, e-poçt və ya mövzu"
              className="max-w-xs"
            />
            <Select
              value={type}
              onChange={(event) => {
                setType(event.target.value);
                setPage(0);
              }}
              className="max-w-[190px]"
            >
              <option value="">Bütün tiplər</option>
              <option value="CONTACT">Əlaqə</option>
              <option value="PRODUCT_INQUIRY">Məhsul sorğusu</option>
              <option value="QUOTE">Qiymət təklifi</option>
              <option value="SUPPORT">Dəstək</option>
              <option value="PARTNERSHIP">Əməkdaşlıq</option>
            </Select>
          </div>

          {requests.isLoading ? (
            <TableSkeleton rows={8} columns={4} />
          ) : !rows || rows.content.length === 0 ? (
            <EmptyState
              icon={<Inbox size={36} aria-hidden="true" />}
              title="Sorğu yoxdur"
              description="Seçilmiş filtrlərə uyğun müraciət tapılmadı."
            />
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {rows.content.map((row) => (
                <li key={row.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedId(row.id);
                      setNoteDraft(null);
                    }}
                    className={clsx(
                      'w-full px-5 py-3.5 text-start transition-colors',
                      selectedId === row.id
                        ? 'bg-[var(--color-primary)]/6'
                        : 'hover:bg-[var(--color-muted)]',
                    )}
                  >
                    <div className="flex items-center gap-2">
                      <span
                        className={clsx(
                          'min-w-0 flex-1 truncate',
                          row.status === 'NEW'
                            ? 'font-semibold text-[var(--color-heading)]'
                            : 'font-medium text-[var(--color-body)]',
                        )}
                      >
                        {row.name}
                        {row.companyName && (
                          <span className="ms-1.5 font-normal text-[var(--color-subtle)]">
                            · {row.companyName}
                          </span>
                        )}
                      </span>
                      <span className="shrink-0 text-xs text-[var(--color-subtle)]">
                        {formatRelative(row.createdAt)}
                      </span>
                    </div>

                    <p className="mt-0.5 truncate text-sm text-[var(--color-body)]">
                      {row.subject ?? row.email}
                    </p>

                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      <Badge tone={STATUS_TONE[row.status]}>{row.statusLabel}</Badge>
                      {row.requestType !== 'CONTACT' && (
                        <Badge tone="neutral">{row.requestTypeLabel}</Badge>
                      )}
                      {row.productName && (
                        <span className="inline-flex items-center gap-1 text-xs text-[var(--color-subtle)]">
                          <Package size={11} aria-hidden="true" />
                          {row.productName}
                        </span>
                      )}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {rows && rows.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
              <span className="text-xs text-[var(--color-subtle)]">
                {formatNumber(rows.totalElements)} sorğu
              </span>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" disabled={rows.first} onClick={() => setPage((p) => p - 1)}>
                  Əvvəlki
                </Button>
                <Button variant="secondary" size="sm" disabled={rows.last} onClick={() => setPage((p) => p + 1)}>
                  Növbəti
                </Button>
              </div>
            </div>
          )}
        </Card>

        {/* ---------------- detail ---------------- */}
        <div className="lg:sticky lg:top-24 lg:self-start">
          {selectedId === null ? (
            <Card>
              <EmptyState
                icon={<Mail size={30} aria-hidden="true" />}
                title="Sorğu seçin"
                description="Tam mətnini oxumaq və cavablandırmaq üçün siyahıdan birinə klikləyin."
              />
            </Card>
          ) : detail.isLoading || !current ? (
            <Card>
              <InlineLoader />
            </Card>
          ) : (
            <div className="space-y-4">
              <Card
                title={current.name}
                description={formatDateTime(current.createdAt)}
                actions={
                  <Button variant="ghost" size="sm" onClick={() => setSelectedId(null)}>
                    Bağla
                  </Button>
                }
              >
                <dl className="space-y-2.5 text-sm">
                  <ContactLine
                    icon={<Mail size={14} aria-hidden="true" />}
                    label="E-poçt"
                    value={current.email}
                    href={`mailto:${current.email}?subject=${encodeURIComponent(
                      `Re: ${current.subject ?? 'Sorğunuz'}`,
                    )}`}
                  />
                  {current.phone && (
                    <ContactLine
                      icon={<Phone size={14} aria-hidden="true" />}
                      label="Telefon"
                      value={current.phone}
                      href={`tel:${current.phone.replace(/\s/g, '')}`}
                    />
                  )}
                  {current.companyName && (
                    <ContactLine
                      icon={<Building2 size={14} aria-hidden="true" />}
                      label="Şirkət"
                      value={current.companyName}
                    />
                  )}
                  {current.productName && current.productSlug && (
                    <div className="flex items-start gap-2">
                      <Package size={14} className="mt-0.5 shrink-0 text-[var(--color-subtle)]" aria-hidden="true" />
                      <div className="min-w-0">
                        <dt className="text-xs text-[var(--color-subtle)]">Məhsul</dt>
                        <dd>
                          <Link
                            to={`/admin/products/${current.productId}`}
                            className="font-medium text-[var(--color-primary)] hover:underline"
                          >
                            {current.productName}
                          </Link>
                        </dd>
                      </div>
                    </div>
                  )}
                </dl>

                {current.subject && (
                  <p className="mt-4 border-t border-[var(--color-border)] pt-4 text-sm font-semibold text-[var(--color-heading)]">
                    {current.subject}
                  </p>
                )}

                <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-[var(--color-body)]">
                  {current.message}
                </p>

                <a
                  href={`mailto:${current.email}?subject=${encodeURIComponent(
                    `Re: ${current.subject ?? 'Sorğunuz'}`,
                  )}`}
                  className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
                >
                  <Mail size={15} aria-hidden="true" />
                  E-poçt ilə cavab yaz
                </a>
                <p className="mt-2 text-center text-xs text-[var(--color-subtle)]">
                  Cavab öz poçt proqramınızdan göndərilir — göndərilmişlər qovluğunuzda qalır.
                </p>
              </Card>

              <Card title="Emal">
                <Field label="Status" htmlFor="requestStatus">
                  <Select
                    id="requestStatus"
                    value={current.status}
                    onChange={(event) =>
                      changeStatus.mutate({ id: current.id, next: event.target.value as Status })
                    }
                  >
                    {STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </Select>
                </Field>

                {current.handledByName && (
                  <p className="mt-3 text-xs text-[var(--color-subtle)]">
                    {current.handledByName} tərəfindən götürülüb
                    {current.handledAt && ` · ${formatDateTime(current.handledAt)}`}
                  </p>
                )}

                <Field label="Daxili qeyd" htmlFor="adminNote" className="mt-4">
                  <Textarea
                    id="adminNote"
                    rows={3}
                    value={noteDraft ?? current.adminNote ?? ''}
                    onChange={(event) => setNoteDraft(event.target.value)}
                    placeholder="Yalnız komandanın görəcəyi qeyd"
                  />
                </Field>

                <Button
                  variant="secondary"
                  size="sm"
                  className="mt-3 w-full"
                  loading={saveNote.isPending}
                  disabled={noteDraft === null}
                  icon={<Save size={14} aria-hidden="true" />}
                  onClick={() => saveNote.mutate({ id: current.id, note: noteDraft ?? '' })}
                >
                  Qeydi yadda saxla
                </Button>

                {current.status !== 'SPAM' && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="mt-2 w-full text-[var(--color-warning)]"
                    icon={<ShieldAlert size={14} aria-hidden="true" />}
                    onClick={() => changeStatus.mutate({ id: current.id, next: 'SPAM' })}
                  >
                    Spam kimi işarələ
                  </Button>
                )}

                {canDelete && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="mt-2 w-full text-[var(--color-danger)]"
                    icon={<Trash2 size={14} aria-hidden="true" />}
                    onClick={() => {
                      if (window.confirm('Sorğu tamamilə silinsin?')) {
                        remove.mutate(current.id);
                      }
                    }}
                  >
                    Sil
                  </Button>
                )}
              </Card>

              {(current.sourceUrl || current.ipAddress) && (
                <Card title="Mənbə">
                  <dl className="space-y-2 text-xs">
                    {current.sourceUrl && (
                      <div className="flex items-start gap-2">
                        <MapPin size={13} className="mt-0.5 shrink-0 text-[var(--color-subtle)]" aria-hidden="true" />
                        <div className="min-w-0">
                          <dt className="text-[var(--color-subtle)]">Səhifə</dt>
                          <dd className="break-all text-[var(--color-body)]">{current.sourceUrl}</dd>
                        </div>
                      </div>
                    )}
                    {current.ipAddress && (
                      <div className="flex justify-between">
                        <dt className="text-[var(--color-subtle)]">IP ünvanı</dt>
                        <dd className="font-mono text-[var(--color-body)]">{current.ipAddress}</dd>
                      </div>
                    )}
                  </dl>
                </Card>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function FilterChip({
  label,
  count,
  tone,
  active,
  onClick,
}: {
  label: string;
  count: number;
  tone?: BadgeTone;
  active: boolean;
  onClick: () => void;
}) {
  const toneClass = {
    info: 'text-[var(--color-primary)]',
    warning: 'text-[var(--color-warning)]',
    success: 'text-[var(--color-success)]',
    danger: 'text-[var(--color-danger)]',
    neutral: 'text-[var(--color-body)]',
    sale: 'text-[var(--color-sale)]',
  }[tone ?? 'neutral'];

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={clsx(
        'flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-medium transition-colors',
        active
          ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/8 text-[var(--color-primary)]'
          : 'border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
      )}
    >
      {label}
      <span className={clsx('text-xs font-bold tabular', !active && toneClass)}>
        {formatNumber(count)}
      </span>
    </button>
  );
}

function ContactLine({
  icon,
  label,
  value,
  href,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  href?: string;
}) {
  return (
    <div className="flex items-start gap-2">
      <span className="mt-0.5 shrink-0 text-[var(--color-subtle)]">{icon}</span>
      <div className="min-w-0">
        <dt className="text-xs text-[var(--color-subtle)]">{label}</dt>
        <dd className="truncate">
          {href ? (
            <a href={href} className="text-[var(--color-primary)] hover:underline">
              {value}
            </a>
          ) : (
            <span className="text-[var(--color-heading)]">{value}</span>
          )}
        </dd>
      </div>
    </div>
  );
}
