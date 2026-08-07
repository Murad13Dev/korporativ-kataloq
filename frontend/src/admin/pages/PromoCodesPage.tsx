import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Copy, Pause, Play, Plus, Shuffle, Ticket, Trash2, Users } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  InlineLoader,
  PageHeader,
  Select,
  TableSkeleton,
  Toggle,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, getPage, patchData, postData, putData } from '@/lib/api';
import { formatDate, formatDateTime, formatNumber, formatPrice } from '@/lib/format';

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';
type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface PromoCode {
  id: number;
  code: string;
  description: string | null;
  discountType: DiscountType;
  value: number;
  maxDiscountAmount: number | null;
  minSubtotal: number;
  freeDelivery: boolean;
  startsAt: string | null;
  endsAt: string | null;
  usageLimit: number | null;
  usageLimitPerCustomer: number;
  usedCount: number;
  excludeDiscounted: boolean;
  status: Status;
  running: boolean;
  scheduled: boolean;
  expired: boolean;
  exhausted: boolean;
  createdAt: string;
}

interface Redemption {
  id: number;
  orderId: number | null;
  orderNumber: string | null;
  customerId: number | null;
  customerEmail: string | null;
  amount: number;
  createdAt: string;
}

interface PromoForm {
  id: number | null;
  code: string;
  description: string;
  discountType: DiscountType;
  value: number;
  maxDiscountAmount: number | null;
  minSubtotal: number;
  freeDelivery: boolean;
  startsAt: string;
  endsAt: string;
  usageLimit: number | null;
  usageLimitPerCustomer: number;
  excludeDiscounted: boolean;
  status: Status;
}

const EMPTY_FORM: PromoForm = {
  id: null,
  code: '',
  description: '',
  discountType: 'PERCENTAGE',
  value: 10,
  maxDiscountAmount: null,
  minSubtotal: 0,
  freeDelivery: false,
  startsAt: '',
  endsAt: '',
  usageLimit: null,
  usageLimitPerCustomer: 1,
  excludeDiscounted: false,
  status: 'ACTIVE',
};

function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function toIso(local: string): string | null {
  return local ? new Date(local).toISOString() : null;
}

/**
 * Promo code management.
 *
 * List and editor sit side by side: vouchers are usually created in small batches for one
 * campaign, and the usage counters on the list are what tell an operator whether the last
 * one worked before they write the next.
 */
export function PromoCodesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<PromoForm | null>(null);
  const [historyFor, setHistoryFor] = useState<PromoCode | null>(null);

  const codes = useQuery({
    queryKey: ['admin', 'promo-codes', { search, status, page }],
    queryFn: () =>
      getPage<PromoCode>('/admin/promo-codes', {
        search: search || undefined,
        status: status || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
  });

  const redemptions = useQuery({
    queryKey: ['admin', 'promo-codes', historyFor?.id, 'redemptions'],
    queryFn: () => getData<Redemption[]>(`/admin/promo-codes/${historyFor!.id}/redemptions`),
    enabled: Boolean(historyFor),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'promo-codes'] });

  const save = useMutation({
    mutationFn: (values: PromoForm) => {
      const payload = {
        code: values.code,
        description: values.description || null,
        discountType: values.discountType,
        value: values.value,
        maxDiscountAmount: values.maxDiscountAmount,
        minSubtotal: values.minSubtotal,
        freeDelivery: values.freeDelivery,
        startsAt: toIso(values.startsAt),
        endsAt: toIso(values.endsAt),
        usageLimit: values.usageLimit,
        usageLimitPerCustomer: values.usageLimitPerCustomer,
        excludeDiscounted: values.excludeDiscounted,
        status: values.status,
      };
      return values.id
        ? putData(`/admin/promo-codes/${values.id}`, payload)
        : postData('/admin/promo-codes', payload);
    },
    onSuccess: () => {
      invalidate();
      setForm(null);
      toast.success('Promo kod yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: number; next: Status }) =>
      patchData(`/admin/promo-codes/${id}/status`, { status: next }),
    onSuccess: invalidate,
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/promo-codes/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Promo kod silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const generate = useMutation({
    mutationFn: (prefix: string) =>
      getData<{ code: string }>('/admin/promo-codes/generate', { prefix: prefix || undefined }),
    onSuccess: (result) => setForm((current) => (current ? { ...current, code: result.code } : current)),
  });

  const rows = codes.data;

  return (
    <>
      <PageHeader
        title="Promo kodlar"
        description="Səbət səviyyəli kuponlar. Məhsul kampaniyaları ilə birlikdə tətbiq olunur."
        actions={
          <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setForm({ ...EMPTY_FORM })}>
            Yeni promo kod
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Card bodyClassName="p-0">
          <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Kod"
              className="max-w-xs"
            />
            <Select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
              className="max-w-[170px]"
            >
              <option value="">Bütün statuslar</option>
              <option value="ACTIVE">Aktiv</option>
              <option value="DRAFT">Qaralama</option>
              <option value="ARCHIVED">Arxiv</option>
            </Select>
          </div>

          {codes.isLoading ? (
            <TableSkeleton rows={6} columns={5} />
          ) : !rows || rows.content.length === 0 ? (
            <EmptyState
              icon={<Ticket size={36} aria-hidden="true" />}
              title="Promo kod yoxdur"
              description="Müştərilərə paylaya biləcəyiniz ilk kuponu yaradın."
              action={
                <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setForm({ ...EMPTY_FORM })}>
                  Yeni promo kod
                </Button>
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                    <th className="px-5 py-3 text-start font-semibold">Kod</th>
                    <th className="px-5 py-3 text-start font-semibold">Endirim</th>
                    <th className="px-5 py-3 text-start font-semibold">İstifadə</th>
                    <th className="px-5 py-3 text-start font-semibold">Vəziyyət</th>
                    <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border)]">
                  {rows.content.map((promo) => (
                    <tr key={promo.id} className="transition-colors hover:bg-[var(--color-muted)]">
                      <td className="px-5 py-3.5">
                        <button
                          type="button"
                          onClick={() => {
                            void navigator.clipboard.writeText(promo.code);
                            toast.success('Kod kopyalandı');
                          }}
                          title="Kodu kopyala"
                          className="group inline-flex items-center gap-1.5 font-mono font-semibold text-[var(--color-heading)]"
                        >
                          {promo.code}
                          <Copy
                            size={13}
                            className="opacity-0 transition-opacity group-hover:opacity-60"
                            aria-hidden="true"
                          />
                        </button>
                        {promo.description && (
                          <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                            {promo.description}
                          </p>
                        )}
                      </td>

                      <td className="px-5 py-3.5">
                        <span className="font-semibold tabular text-[var(--color-sale)]">
                          {promo.freeDelivery && promo.value === 0
                            ? 'Pulsuz çatdırılma'
                            : promo.discountType === 'PERCENTAGE'
                              ? `−${promo.value}%`
                              : `−${formatPrice(promo.value)}`}
                        </span>
                        {promo.minSubtotal > 0 && (
                          <p className="text-xs text-[var(--color-subtle)]">
                            min. {formatPrice(promo.minSubtotal)}
                          </p>
                        )}
                      </td>

                      <td className="px-5 py-3.5">
                        <span className="tabular text-[var(--color-heading)]">
                          {formatNumber(promo.usedCount)}
                          {promo.usageLimit !== null && (
                            <span className="text-[var(--color-subtle)]">
                              {' / '}
                              {formatNumber(promo.usageLimit)}
                            </span>
                          )}
                        </span>
                        {promo.usageLimit !== null && (
                          <div className="mt-1 h-1 w-16 overflow-hidden rounded-full bg-[var(--color-border)]">
                            <div
                              className={clsx(
                                'h-full rounded-full',
                                promo.exhausted ? 'bg-[var(--color-danger)]' : 'bg-[var(--color-primary)]',
                              )}
                              style={{
                                width: `${Math.min((promo.usedCount / promo.usageLimit) * 100, 100)}%`,
                              }}
                            />
                          </div>
                        )}
                      </td>

                      <td className="px-5 py-3.5">
                        <StateBadge promo={promo} />
                        {promo.endsAt && (
                          <p className="mt-1 text-xs text-[var(--color-subtle)]">
                            {formatDate(promo.endsAt)}-dək
                          </p>
                        )}
                      </td>

                      <td className="px-5 py-3.5">
                        <div className="flex items-center justify-end gap-0.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            title="İstifadə tarixçəsi"
                            onClick={() => setHistoryFor(promo)}
                            icon={<Users size={15} aria-hidden="true" />}
                          />
                          <Button
                            variant="ghost"
                            size="sm"
                            title={promo.status === 'ACTIVE' ? 'Dayandır' : 'Aktivləşdir'}
                            onClick={() =>
                              changeStatus.mutate({
                                id: promo.id,
                                next: promo.status === 'ACTIVE' ? 'DRAFT' : 'ACTIVE',
                              })
                            }
                            icon={
                              promo.status === 'ACTIVE' ? (
                                <Pause size={15} aria-hidden="true" />
                              ) : (
                                <Play size={15} aria-hidden="true" />
                              )
                            }
                          />
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Redaktə et"
                            onClick={() =>
                              setForm({
                                id: promo.id,
                                code: promo.code,
                                description: promo.description ?? '',
                                discountType: promo.discountType,
                                value: promo.value,
                                maxDiscountAmount: promo.maxDiscountAmount,
                                minSubtotal: promo.minSubtotal,
                                freeDelivery: promo.freeDelivery,
                                startsAt: toLocalInput(promo.startsAt),
                                endsAt: toLocalInput(promo.endsAt),
                                usageLimit: promo.usageLimit,
                                usageLimitPerCustomer: promo.usageLimitPerCustomer,
                                excludeDiscounted: promo.excludeDiscounted,
                                status: promo.status,
                              })
                            }
                            icon={<Ticket size={15} aria-hidden="true" />}
                          />
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Sil"
                            className="text-[var(--color-danger)]"
                            onClick={() => {
                              if (window.confirm(`"${promo.code}" silinsin?`)) {
                                remove.mutate(promo.id);
                              }
                            }}
                            icon={<Trash2 size={15} aria-hidden="true" />}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {rows && rows.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
              <span className="text-xs text-[var(--color-subtle)]">
                {formatNumber(rows.totalElements)} kod
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

        {/* ---------------- editor / history ---------------- */}
        <div className="lg:sticky lg:top-24 lg:self-start">
          {form ? (
            <Card
              title={form.id ? 'Promo kodu redaktə et' : 'Yeni promo kod'}
              actions={
                <Button variant="ghost" size="sm" onClick={() => setForm(null)}>
                  Bağla
                </Button>
              }
            >
              <div className="space-y-4">
                <Field label="Kod" htmlFor="code" required hint="Müştəri bunu checkout-da yazır">
                  <div className="flex gap-2">
                    <Input
                      id="code"
                      value={form.code}
                      onChange={(event) =>
                        setForm({ ...form, code: event.target.value.toUpperCase() })
                      }
                      placeholder="XOSGELDIN10"
                      className="font-mono"
                    />
                    <Button
                      variant="secondary"
                      size="sm"
                      title="Təsadüfi kod yarat"
                      loading={generate.isPending}
                      onClick={() => generate.mutate('')}
                      icon={<Shuffle size={14} aria-hidden="true" />}
                    />
                  </div>
                </Field>

                <Field label="Təsvir" htmlFor="promoDescription" hint="Yalnız admin panelində görünür">
                  <Input
                    id="promoDescription"
                    value={form.description}
                    onChange={(event) => setForm({ ...form, description: event.target.value })}
                  />
                </Field>

                <Toggle
                  label="Pulsuz çatdırılma"
                  hint="Çatdırılma haqqını sıfırlayır"
                  checked={form.freeDelivery}
                  onChange={(checked) =>
                    setForm({ ...form, freeDelivery: checked, value: checked ? 0 : form.value })
                  }
                />

                {!(form.freeDelivery && form.value === 0) && (
                  <div className="grid grid-cols-2 gap-3">
                    <Field label="Tip" htmlFor="promoType">
                      <Select
                        id="promoType"
                        value={form.discountType}
                        onChange={(event) =>
                          setForm({ ...form, discountType: event.target.value as DiscountType })
                        }
                      >
                        <option value="PERCENTAGE">Faiz (%)</option>
                        <option value="FIXED_AMOUNT">Məbləğ (₼)</option>
                      </Select>
                    </Field>

                    <Field label="Dəyər" htmlFor="promoValue" required>
                      <Input
                        id="promoValue"
                        type="number"
                        step="0.01"
                        min="0"
                        max={form.discountType === 'PERCENTAGE' ? 100 : undefined}
                        value={form.value}
                        onChange={(event) => setForm({ ...form, value: Number(event.target.value) })}
                      />
                    </Field>
                  </div>
                )}

                {form.discountType === 'PERCENTAGE' && !form.freeDelivery && (
                  <Field
                    label="Maksimum endirim"
                    htmlFor="promoMax"
                    hint="Boş = limitsiz. Məsələn: 20%, amma ən çox 50 ₼"
                  >
                    <Input
                      id="promoMax"
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.maxDiscountAmount ?? ''}
                      onChange={(event) =>
                        setForm({
                          ...form,
                          maxDiscountAmount: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                      placeholder="Limitsiz"
                    />
                  </Field>
                )}

                <Field
                  label="Minimum səbət məbləği"
                  htmlFor="promoMin"
                  hint="Bu məbləğdən aşağı səbətlərdə kod işləmir"
                >
                  <Input
                    id="promoMin"
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.minSubtotal}
                    onChange={(event) => setForm({ ...form, minSubtotal: Number(event.target.value) })}
                  />
                </Field>

                <div className="grid grid-cols-2 gap-3">
                  <Field label="Başlama" htmlFor="promoStart">
                    <Input
                      id="promoStart"
                      type="datetime-local"
                      value={form.startsAt}
                      onChange={(event) => setForm({ ...form, startsAt: event.target.value })}
                    />
                  </Field>
                  <Field label="Bitmə" htmlFor="promoEnd">
                    <Input
                      id="promoEnd"
                      type="datetime-local"
                      value={form.endsAt}
                      onChange={(event) => setForm({ ...form, endsAt: event.target.value })}
                    />
                  </Field>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <Field label="Ümumi limit" htmlFor="promoLimit" hint="Boş = limitsiz">
                    <Input
                      id="promoLimit"
                      type="number"
                      min="1"
                      value={form.usageLimit ?? ''}
                      onChange={(event) =>
                        setForm({
                          ...form,
                          usageLimit: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                      placeholder="∞"
                    />
                  </Field>
                  <Field label="Müştəri başına" htmlFor="promoPerCustomer">
                    <Input
                      id="promoPerCustomer"
                      type="number"
                      min="0"
                      value={form.usageLimitPerCustomer}
                      onChange={(event) =>
                        setForm({ ...form, usageLimitPerCustomer: Number(event.target.value) })
                      }
                    />
                  </Field>
                </div>

                <Toggle
                  label="Endirimli məhsullara tətbiq etmə"
                  hint="Kampaniyada olan məhsulların məbləği kodun hesablanmasından çıxarılır"
                  checked={form.excludeDiscounted}
                  onChange={(checked) => setForm({ ...form, excludeDiscounted: checked })}
                />

                <Field label="Status" htmlFor="promoStatus">
                  <Select
                    id="promoStatus"
                    value={form.status}
                    onChange={(event) => setForm({ ...form, status: event.target.value as Status })}
                  >
                    <option value="ACTIVE">Aktiv</option>
                    <option value="DRAFT">Qaralama</option>
                    <option value="ARCHIVED">Arxiv</option>
                  </Select>
                </Field>

                <Button
                  className="w-full"
                  loading={save.isPending}
                  disabled={!form.code.trim()}
                  onClick={() => save.mutate(form)}
                >
                  Yadda saxla
                </Button>
              </div>
            </Card>
          ) : historyFor ? (
            <Card
              title={`${historyFor.code} — istifadə`}
              description={`${formatNumber(historyFor.usedCount)} dəfə işlədilib`}
              bodyClassName="p-0"
              actions={
                <Button variant="ghost" size="sm" onClick={() => setHistoryFor(null)}>
                  Bağla
                </Button>
              }
            >
              {redemptions.isLoading ? (
                <InlineLoader />
              ) : !redemptions.data || redemptions.data.length === 0 ? (
                <p className="px-5 py-10 text-center text-sm text-[var(--color-subtle)]">
                  Bu kod hələ istifadə edilməyib
                </p>
              ) : (
                <ul className="max-h-[28rem] divide-y divide-[var(--color-border)] overflow-y-auto">
                  {redemptions.data.map((entry) => (
                    <li key={entry.id} className="px-5 py-3">
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="truncate text-sm text-[var(--color-heading)]">
                          {entry.customerEmail ?? 'Qonaq'}
                        </span>
                        <span className="shrink-0 text-sm font-semibold tabular text-[var(--color-success)]">
                          −{formatPrice(entry.amount)}
                        </span>
                      </div>
                      <p className="text-xs text-[var(--color-subtle)]">
                        {entry.orderNumber ?? '—'} · {formatDateTime(entry.createdAt)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ) : (
            <Card title="Necə işləyir">
              <ul className="space-y-3 text-sm text-[var(--color-body)]">
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Promo kod <strong className="text-[var(--color-heading)]">səbətin yekununa</strong>{' '}
                    tətbiq olunur, məhsulun qiymətinə yox.
                  </span>
                </li>
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Məhsul kampaniyaları ilə{' '}
                    <strong className="text-[var(--color-heading)]">birlikdə işləyir</strong> — kod
                    kampaniyaların formalaşdırdığı ara cəmə tətbiq edilir.
                  </span>
                </li>
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Kodun şərtləri hər səbət oxunuşunda yenidən yoxlanılır — vaxtı bitən kod
                    özü düşür.
                  </span>
                </li>
              </ul>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function StateBadge({ promo }: { promo: PromoCode }) {
  let tone: BadgeTone = 'neutral';
  let label = 'Qaralama';

  if (promo.status === 'ARCHIVED') {
    label = 'Arxiv';
  } else if (promo.exhausted) {
    tone = 'danger';
    label = 'Limit dolub';
  } else if (promo.expired) {
    label = 'Vaxtı bitib';
  } else if (promo.running) {
    tone = 'success';
    label = 'İşləyir';
  } else if (promo.scheduled) {
    tone = 'info';
    label = 'Planlaşdırılıb';
  }

  return <Badge tone={tone}>{label}</Badge>;
}
