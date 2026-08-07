import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm, useWatch } from 'react-hook-form';
import clsx from 'clsx';
import { ArrowLeft, Eye, Save } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  Field,
  InlineLoader,
  Input,
  PageHeader,
  Select,
  Textarea,
  Toggle,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, getData, postData, putData } from '@/lib/api';
import { formatNumber, formatPrice } from '@/lib/format';
import { ribbonColor } from '@/lib/theme';
import type { CategoryCard } from '@/types/api';

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';
type DiscountScope = 'ALL_PRODUCTS' | 'CATEGORIES' | 'PRODUCTS';
type BadgeStyle = 'SALE' | 'HOT' | 'SEASONAL' | 'CLEARANCE' | 'BUNDLE' | 'CUSTOM';
type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface DiscountForm {
  name: string;
  description: string;
  discountType: DiscountType;
  value: number;
  maxDiscountAmount: number | null;
  scope: DiscountScope;
  productIds: number[];
  categoryIds: number[];
  startsAt: string;
  endsAt: string;
  priority: number;
  showBadge: boolean;
  badgeLabel: string;
  badgeStyle: BadgeStyle;
  badgeColor: string;
  showCountdown: boolean;
  status: Status;
}

interface PreviewResponse {
  affectedProductCount: number;
  totalListValue: number;
  totalDiscountedValue: number;
  sample: {
    productId: number;
    sku: string;
    name: string;
    listPrice: number;
    finalPrice: number;
    saving: number;
    savingPercentage: number;
    badgeLabel: string;
  }[];
}

const BADGE_STYLES: { value: BadgeStyle; label: string }[] = [
  { value: 'SALE', label: 'Endirim (standart)' },
  { value: 'HOT', label: 'Qaynar təklif' },
  { value: 'SEASONAL', label: 'Mövsümi' },
  { value: 'CLEARANCE', label: 'Anbar təmizlənməsi' },
  { value: 'BUNDLE', label: 'Dəst' },
  { value: 'CUSTOM', label: 'Xüsusi rəng' },
];

/** ISO instant → the `datetime-local` value an input expects, in local time. */
function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

function toIso(local: string): string | null {
  return local ? new Date(local).toISOString() : null;
}

export function DiscountEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [showPreview, setShowPreview] = useState(false);

  const existing = useQuery({
    queryKey: ['admin', 'discount', id],
    queryFn: () => getData<any>(`/admin/discounts/${id}`),
    enabled: !isNew,
  });

  const categories = useQuery({
    queryKey: ['admin', 'categories', 'flat'],
    queryFn: () => getData<CategoryCard[]>('/admin/categories'),
    staleTime: 5 * 60 * 1000,
  });

  const {
    register,
    handleSubmit,
    control,
    reset,
    setValue,
    formState: { errors, isDirty },
  } = useForm<DiscountForm>({
    defaultValues: {
      name: '',
      description: '',
      discountType: 'PERCENTAGE',
      value: 10,
      maxDiscountAmount: null,
      scope: 'ALL_PRODUCTS',
      productIds: [],
      categoryIds: [],
      startsAt: '',
      endsAt: '',
      priority: 0,
      showBadge: true,
      badgeLabel: '',
      badgeStyle: 'SALE',
      badgeColor: '',
      showCountdown: false,
      status: 'DRAFT',
    },
  });

  useEffect(() => {
    if (existing.data) {
      const data = existing.data;
      reset({
        name: data.name,
        description: data.description ?? '',
        discountType: data.discountType,
        value: data.value,
        maxDiscountAmount: data.maxDiscountAmount,
        scope: data.scope,
        productIds: data.productIds ?? [],
        categoryIds: data.categoryIds ?? [],
        startsAt: toLocalInput(data.startsAt),
        endsAt: toLocalInput(data.endsAt),
        priority: data.priority,
        showBadge: data.showBadge,
        badgeLabel: data.badgeLabel ?? '',
        badgeStyle: data.badgeStyle,
        badgeColor: data.badgeColor ?? '',
        showCountdown: data.showCountdown,
        status: data.status,
      });
    }
  }, [existing.data, reset]);

  // Watched for the live ribbon preview — this is what makes the badge settings
  // legible without saving and switching to the storefront.
  const watched = useWatch({ control });

  const save = useMutation({
    mutationFn: (values: DiscountForm) => {
      const payload = {
        ...values,
        description: values.description || null,
        badgeLabel: values.badgeLabel || null,
        badgeColor: values.badgeStyle === 'CUSTOM' ? values.badgeColor : null,
        maxDiscountAmount: values.maxDiscountAmount || null,
        startsAt: toIso(values.startsAt),
        endsAt: toIso(values.endsAt),
        productIds: values.scope === 'PRODUCTS' ? values.productIds : [],
        categoryIds: values.scope === 'CATEGORIES' ? values.categoryIds : [],
      };
      return isNew
        ? postData<{ id: number }>('/admin/discounts', payload)
        : putData<{ id: number }>(`/admin/discounts/${id}`, payload);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'discounts'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success(isNew ? 'Kampaniya yaradıldı' : 'Kampaniya yeniləndi');
      if (isNew) {
        navigate(`/admin/discounts/${saved.id}`, { replace: true });
      }
    },
    onError: (error: ApiError) => {
      const fields = Object.entries(error.fieldErrors);
      if (fields.length > 0) {
        toast.error(fields.map(([field, message]) => `${field}: ${message}`).join('\n'));
      } else {
        toast.error(error.message);
      }
    },
  });

  const preview = useQuery({
    queryKey: ['admin', 'discount', id, 'preview'],
    queryFn: () => getData<PreviewResponse>(`/admin/discounts/${id}/preview`),
    enabled: showPreview && !isNew,
  });

  if (!isNew && existing.isLoading) {
    return <InlineLoader label="Kampaniya yüklənir…" />;
  }

  const onSubmit = handleSubmit((values) => save.mutate(values));

  return (
    <form onSubmit={onSubmit} noValidate>
      <PageHeader
        breadcrumb={
          <Link
            to="/admin/discounts"
            className="inline-flex items-center gap-1.5 hover:text-[var(--color-heading)]"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            Kampaniyalar
          </Link>
        }
        title={isNew ? 'Yeni kampaniya' : (existing.data?.name ?? 'Kampaniya')}
        actions={
          <>
            {!isNew && (
              <Button
                type="button"
                variant="secondary"
                icon={<Eye size={15} aria-hidden="true" />}
                onClick={() => setShowPreview((open) => !open)}
              >
                Təsiri gör
              </Button>
            )}
            <Button type="submit" loading={save.isPending} icon={<Save size={15} aria-hidden="true" />}>
              Yadda saxla
            </Button>
          </>
        }
      />

      {/* Preview: what the campaign would actually do, before switching it on. */}
      {showPreview && !isNew && (
        <Card
          title="Kampaniyanın təsiri"
          description="Bu hesablama heç nəyi dəyişmir."
          className="mb-6"
        >
          {preview.isLoading ? (
            <InlineLoader />
          ) : preview.data ? (
            <>
              <div className="mb-5 grid gap-4 sm:grid-cols-3">
                <PreviewStat
                  label="Təsirlənən məhsul"
                  value={formatNumber(preview.data.affectedProductCount)}
                />
                <PreviewStat
                  label="Kataloq dəyəri"
                  value={formatPrice(preview.data.totalListValue)}
                />
                <PreviewStat
                  label="Endirimli dəyər"
                  value={formatPrice(preview.data.totalDiscountedValue)}
                  tone="sale"
                />
              </div>

              {preview.data.sample.length > 0 && (
                <div className="overflow-x-auto rounded-[var(--radius-button)] border border-[var(--color-border)]">
                  <table className="w-full text-sm">
                    <thead className="bg-[var(--color-muted)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                      <tr>
                        <th className="px-4 py-2.5 text-start font-semibold">Məhsul</th>
                        <th className="px-4 py-2.5 text-end font-semibold">Kataloq</th>
                        <th className="px-4 py-2.5 text-end font-semibold">Yeni</th>
                        <th className="px-4 py-2.5 text-end font-semibold">Qənaət</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--color-border)]">
                      {preview.data.sample.map((line) => (
                        <tr key={line.productId}>
                          <td className="px-4 py-2.5">
                            <span className="font-medium text-[var(--color-heading)]">{line.name}</span>
                            <span className="ms-2 text-xs text-[var(--color-subtle)]">{line.sku}</span>
                          </td>
                          <td className="px-4 py-2.5 text-end tabular text-[var(--color-subtle)] line-through">
                            {formatPrice(line.listPrice)}
                          </td>
                          <td className="px-4 py-2.5 text-end font-semibold tabular text-[var(--color-sale)]">
                            {formatPrice(line.finalPrice)}
                          </td>
                          <td className="px-4 py-2.5 text-end tabular text-[var(--color-success)]">
                            −{formatPrice(line.saving)} ({line.savingPercentage}%)
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          ) : null}
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          {/* ---------------- basics ---------------- */}
          <Card title="Kampaniya">
            <div className="space-y-4">
              <Field label="Ad" htmlFor="name" error={errors.name?.message} required>
                <Input
                  id="name"
                  placeholder="Yaz kampaniyası"
                  hasError={Boolean(errors.name)}
                  {...register('name', { required: 'Ad tələb olunur' })}
                />
              </Field>

              <Field label="Təsvir" htmlFor="description" hint="Yalnız admin panelində görünür">
                <Textarea id="description" rows={2} {...register('description')} />
              </Field>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Endirim tipi" htmlFor="discountType">
                  <Select id="discountType" {...register('discountType')}>
                    <option value="PERCENTAGE">Faiz (%)</option>
                    <option value="FIXED_AMOUNT">Sabit məbləğ (₼)</option>
                  </Select>
                </Field>

                <Field
                  label={watched.discountType === 'PERCENTAGE' ? 'Faiz' : 'Məbləğ'}
                  htmlFor="value"
                  error={errors.value?.message}
                  required
                >
                  <Input
                    id="value"
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={watched.discountType === 'PERCENTAGE' ? 100 : undefined}
                    hasError={Boolean(errors.value)}
                    {...register('value', {
                      required: 'Dəyər tələb olunur',
                      valueAsNumber: true,
                      min: { value: 0.01, message: '0-dan böyük olmalıdır' },
                      max:
                        watched.discountType === 'PERCENTAGE'
                          ? { value: 100, message: '100-dən böyük ola bilməz' }
                          : undefined,
                    })}
                  />
                </Field>
              </div>

              {watched.discountType === 'PERCENTAGE' && (
                <Field
                  label="Maksimum endirim məbləği"
                  htmlFor="maxDiscountAmount"
                  hint="Boş qoyulsa limit yoxdur. Məsələn: 30% endirim, amma ən çox 50 ₼."
                >
                  <Input
                    id="maxDiscountAmount"
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Limitsiz"
                    {...register('maxDiscountAmount', { valueAsNumber: true })}
                  />
                </Field>
              )}
            </div>
          </Card>

          {/* ---------------- targeting ---------------- */}
          <Card title="Əhatə dairəsi" description="Kampaniya hansı məhsullara tətbiq olunacaq">
            <Field label="Tip" htmlFor="scope">
              <Select id="scope" {...register('scope')}>
                <option value="ALL_PRODUCTS">Bütün məhsullar</option>
                <option value="CATEGORIES">Seçilmiş kateqoriyalar</option>
                <option value="PRODUCTS">Seçilmiş məhsullar</option>
              </Select>
            </Field>

            {watched.scope === 'CATEGORIES' && (
              <div className="mt-4">
                <p className="mb-2 text-sm font-medium text-[var(--color-heading)]">
                  Kateqoriyalar
                  <span className="ms-2 font-normal text-xs text-[var(--color-subtle)]">
                    Alt kateqoriyalar avtomatik daxildir
                  </span>
                </p>
                <Controller
                  control={control}
                  name="categoryIds"
                  render={({ field }) => (
                    <div className="grid max-h-64 gap-1 overflow-y-auto rounded-[var(--radius-button)] border border-[var(--color-border)] p-3 sm:grid-cols-2">
                      {(categories.data ?? []).map((category) => {
                        const checked = field.value?.includes(category.id) ?? false;
                        return (
                          <label
                            key={category.id}
                            className="flex cursor-pointer items-center gap-2.5 rounded px-2 py-1.5 text-sm hover:bg-[var(--color-muted)]"
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={(event) => {
                                const next = event.target.checked
                                  ? [...(field.value ?? []), category.id]
                                  : (field.value ?? []).filter((value) => value !== category.id);
                                field.onChange(next);
                              }}
                              className="h-4 w-4 rounded accent-[var(--color-primary)]"
                            />
                            <span className="flex-1 text-[var(--color-body)]">{category.name}</span>
                            <span className="text-xs text-[var(--color-subtle)] tabular">
                              {category.productCount}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                />
              </div>
            )}

            {watched.scope === 'PRODUCTS' && (
              <div className="mt-4 rounded-[var(--radius-button)] border border-dashed border-[var(--color-border)] p-4 text-sm text-[var(--color-body)]">
                <p className="mb-1 font-medium text-[var(--color-heading)]">
                  Məhsul seçimi
                </p>
                <p className="text-[var(--color-subtle)]">
                  Tək məhsullar üçün ən rahat yol məhsulun öz redaktə səhifəsindəki
                  <strong className="text-[var(--color-heading)]"> “Endirim əlavə et” </strong>
                  düyməsidir — həmin məhsul üçün avtomatik kampaniya yaradır.
                </p>
                <Controller
                  control={control}
                  name="productIds"
                  render={({ field }) => (
                    <p className="mt-3 text-xs text-[var(--color-subtle)]">
                      Seçilmiş məhsul sayı:{' '}
                      <strong className="tabular text-[var(--color-heading)]">
                        {field.value?.length ?? 0}
                      </strong>
                    </p>
                  )}
                />
              </div>
            )}
          </Card>

          {/* ---------------- schedule ---------------- */}
          <Card
            title="Vaxt aralığı"
            description="Boş qoyulsa kampaniya dərhal başlayır və müddətsiz davam edir"
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Başlama" htmlFor="startsAt">
                <Input id="startsAt" type="datetime-local" {...register('startsAt')} />
              </Field>
              <Field label="Bitmə" htmlFor="endsAt">
                <Input id="endsAt" type="datetime-local" {...register('endsAt')} />
              </Field>
            </div>

            <Field
              label="Prioritet"
              htmlFor="priority"
              className="mt-4"
              hint="Bir məhsula bir neçə kampaniya uyğun gələrsə, prioriteti yüksək olan qalib gəlir. Kampaniyalar üst-üstə gəlmir."
            >
              <Input id="priority" type="number" min="0" {...register('priority', { valueAsNumber: true })} />
            </Field>
          </Card>
        </div>

        {/* ---------------- side: badge settings + live preview ---------------- */}
        <div className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          <Card title="Lent görünüşü">
            <RibbonPreview
              badgeLabel={watched.badgeLabel || null}
              badgeStyle={(watched.badgeStyle as BadgeStyle) ?? 'SALE'}
              badgeColor={watched.badgeColor || null}
              discountType={(watched.discountType as DiscountType) ?? 'PERCENTAGE'}
              value={Number(watched.value) || 0}
              showBadge={watched.showBadge ?? true}
              showCountdown={watched.showCountdown ?? false}
            />

            <div className="mt-5 space-y-4">
              <Controller
                control={control}
                name="showBadge"
                render={({ field }) => (
                  <Toggle
                    checked={field.value}
                    onChange={field.onChange}
                    label="Lenti göstər"
                    hint="Söndürülsə qiymət endirimli qalır, lakin lent görünmür"
                  />
                )}
              />

              <Field
                label="Lent mətni"
                htmlFor="badgeLabel"
                hint="Boş qoyulsa avtomatik faiz göstərilir"
              >
                <Input
                  id="badgeLabel"
                  placeholder={
                    watched.discountType === 'PERCENTAGE'
                      ? `-${Number(watched.value) || 0}%`
                      : 'Avtomatik'
                  }
                  maxLength={48}
                  {...register('badgeLabel')}
                />
              </Field>

              <Field label="Stil" htmlFor="badgeStyle">
                <Select id="badgeStyle" {...register('badgeStyle')}>
                  {BADGE_STYLES.map((style) => (
                    <option key={style.value} value={style.value}>
                      {style.label}
                    </option>
                  ))}
                </Select>
              </Field>

              {watched.badgeStyle === 'CUSTOM' && (
                <Field label="Rəng" htmlFor="badgeColor" required>
                  <div className="flex gap-2">
                    <input
                      type="color"
                      value={watched.badgeColor || '#F43F5E'}
                      onChange={(event) => setValue('badgeColor', event.target.value, { shouldDirty: true })}
                      className="h-10 w-14 shrink-0 cursor-pointer rounded-[var(--radius-input)] border border-[var(--color-border)]"
                      aria-label="Lent rəngi"
                    />
                    <Input
                      id="badgeColor"
                      placeholder="#F43F5E"
                      {...register('badgeColor')}
                    />
                  </div>
                </Field>
              )}

              <Controller
                control={control}
                name="showCountdown"
                render={({ field }) => (
                  <Toggle
                    checked={field.value}
                    onChange={field.onChange}
                    label="Geri sayım göstər"
                    hint="Yalnız bitmə tarixi təyin edilibsə işləyir"
                    disabled={!watched.endsAt}
                  />
                )}
              />
            </div>
          </Card>

          <Card title="Status">
            <Field label="Vəziyyət" htmlFor="status">
              <Select id="status" {...register('status')}>
                <option value="DRAFT">Qaralama — işləmir</option>
                <option value="ACTIVE">Aktiv — tarixə görə işləyir</option>
                <option value="ARCHIVED">Arxiv</option>
              </Select>
            </Field>

            {watched.status === 'ACTIVE' && (
              <p className="mt-3 rounded-[var(--radius-button)] bg-[var(--color-warning)]/10 px-3 py-2.5 text-xs text-[var(--color-warning)]">
                Yadda saxlandıqdan sonra kampaniya təyin edilmiş tarixdə avtomatik başlayacaq
                və müştərilər endirimli qiyməti görəcək.
              </p>
            )}

            {isDirty && (
              <p className="mt-3 text-xs text-[var(--color-subtle)]">Yadda saxlanmamış dəyişikliklər var.</p>
            )}
          </Card>
        </div>
      </div>
    </form>
  );
}

function PreviewStat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: 'sale';
}) {
  return (
    <div className="rounded-[var(--radius-button)] bg-[var(--color-muted)] p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
        {label}
      </p>
      <p
        className={clsx(
          'mt-1 font-display text-xl font-bold tabular',
          tone === 'sale' ? 'text-[var(--color-sale)]' : 'text-[var(--color-heading)]',
        )}
      >
        {value}
      </p>
    </div>
  );
}

/**
 * Live ribbon preview.
 *
 * Renders against a sample price so the operator sees the exact card a shopper will —
 * including the automatic label, the strike-through and the countdown slot. Without this
 * the badge settings are three text fields with no visible consequence.
 */
function RibbonPreview({
  badgeLabel,
  badgeStyle,
  badgeColor,
  discountType,
  value,
  showBadge,
  showCountdown,
}: {
  badgeLabel: string | null;
  badgeStyle: BadgeStyle;
  badgeColor: string | null;
  discountType: DiscountType;
  value: number;
  showBadge: boolean;
  showCountdown: boolean;
}) {
  const SAMPLE_PRICE = 249;

  const { finalPrice, percentage, label } = useMemo(() => {
    const saving =
      discountType === 'PERCENTAGE' ? (SAMPLE_PRICE * value) / 100 : Math.min(value, SAMPLE_PRICE);
    const final = Math.max(SAMPLE_PRICE - saving, 0);
    const percent = Math.round((saving / SAMPLE_PRICE) * 100);
    return {
      finalPrice: final,
      percentage: percent,
      label: badgeLabel || (percent > 0 ? `-${percent}%` : `-${saving.toFixed(2)} ₼`),
    };
  }, [discountType, value, badgeLabel]);

  return (
    <div>
      <p className="mb-3 text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
        Müştəri belə görəcək
      </p>

      <div className="relative overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)]">
        {showBadge && (
          <div className="ribbon" aria-hidden="true">
            <span
              className="ribbon__label"
              style={{ '--ribbon-color': ribbonColor(badgeStyle, badgeColor) } as React.CSSProperties}
            >
              {label}
            </span>
          </div>
        )}

        <div className="flex aspect-[4/3] items-center justify-center bg-[var(--color-muted)] text-xs text-[var(--color-subtle)]">
          məhsul şəkli
        </div>

        <div className="p-3">
          <p className="mb-1.5 text-sm font-semibold text-[var(--color-heading)]">Nümunə məhsul</p>
          <div className="flex items-baseline gap-2">
            <span className="price-now text-lg">{formatPrice(finalPrice)}</span>
            <span className="price-was text-sm">{formatPrice(SAMPLE_PRICE)}</span>
          </div>
          {showCountdown && (
            <p className="mt-1.5 text-xs font-medium text-[var(--color-sale)]">⏱ 4g 12s qalıb</p>
          )}
        </div>
      </div>

      <div className="mt-3 flex items-center justify-between text-xs">
        <span className="text-[var(--color-subtle)]">Faktiki endirim</span>
        <Badge tone="sale">{percentage}%</Badge>
      </div>
    </div>
  );
}
