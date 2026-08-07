import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  BarChart3,
  Building2,
  HelpCircle,
  Image as ImageIcon,
  Pencil,
  Plus,
  Quote,
  ShieldCheck,
  Star,
  Trash2,
  X,
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
  Textarea,
  Toggle,
  type BadgeTone,
} from '../components/ui';
import { MediaPicker, type PickedMedia } from '../components/MediaPicker';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData, putData } from '@/lib/api';
import type { Language } from '@/types/api';

type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

const STATUS_TONE: Record<Status, BadgeTone> = {
  ACTIVE: 'success',
  DRAFT: 'warning',
  ARCHIVED: 'neutral',
};

const STATUS_LABEL: Record<Status, string> = {
  ACTIVE: 'Aktiv',
  DRAFT: 'Qaralama',
  ARCHIVED: 'Arxiv',
};

function useLanguages(): Language[] {
  const query = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });
  return query.data ?? [{ code: 'az' } as Language];
}

/** Status + sort-order fields shared by every editor form. */
function StatusAndSort({
  status,
  sortOrder,
  onStatusChange,
  onSortOrderChange,
}: {
  status: Status;
  sortOrder: number;
  onStatusChange: (status: Status) => void;
  onSortOrderChange: (sortOrder: number) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-3">
      <Field label="Status" htmlFor="cbStatus">
        <Select
          id="cbStatus"
          value={status}
          onChange={(event) => onStatusChange(event.target.value as Status)}
        >
          <option value="ACTIVE">Aktiv</option>
          <option value="DRAFT">Qaralama</option>
          <option value="ARCHIVED">Arxiv</option>
        </Select>
      </Field>
      <Field label="Sıra" htmlFor="cbSort">
        <Input
          id="cbSort"
          type="number"
          value={sortOrder}
          onChange={(event) => onSortOrderChange(Number(event.target.value))}
        />
      </Field>
    </div>
  );
}

/** Per-language tab switcher used above a translated text field. */
function LanguageTabs({
  languages,
  active,
  onChange,
}: {
  languages: Language[];
  active: string;
  onChange: (code: string) => void;
}) {
  if (languages.length <= 1) {
    return null;
  }
  return (
    <div className="mb-1.5 flex gap-1">
      {languages.map((language) => (
        <button
          key={language.code}
          type="button"
          onClick={() => onChange(language.code)}
          className={clsx(
            'rounded px-2 py-0.5 text-xs font-semibold uppercase transition-colors',
            active === language.code
              ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
              : 'text-[var(--color-subtle)] hover:bg-[var(--color-muted)]',
          )}
        >
          {language.code}
        </button>
      ))}
    </div>
  );
}

const TABS = [
  { key: 'benefits', label: 'Üstünlüklər', icon: ShieldCheck },
  { key: 'statistics', label: 'Statistika', icon: BarChart3 },
  { key: 'testimonials', label: 'Rəylər', icon: Quote },
  { key: 'partners', label: 'Partnyorlar', icon: Building2 },
  { key: 'faqs', label: 'FAQ', icon: HelpCircle },
] as const;

type TabKey = (typeof TABS)[number]['key'];

/**
 * The small corporate content blocks the public sections render: "why choose us" cards,
 * counter tiles, customer reviews, partner logos and FAQ entries. One screen with tabs
 * rather than five nav entries — each block type is a handful of rows, not a module.
 */
export function ContentBlocksPage() {
  const [tab, setTab] = useState<TabKey>('benefits');

  return (
    <>
      <PageHeader
        title="Kontent blokları"
        description="Ana səhifə və digər bölmələrin oxuduğu kiçik məzmun bloklari: üstünlüklər, statistika, rəylər, partnyorlar, FAQ."
      />

      <div className="mb-6 flex flex-wrap gap-2 border-b border-[var(--color-border)] pb-3">
        {TABS.map((entry) => {
          const Icon = entry.icon;
          return (
            <button
              key={entry.key}
              type="button"
              onClick={() => setTab(entry.key)}
              className={clsx(
                'flex items-center gap-2 rounded-[var(--radius-button)] px-3.5 py-2 text-sm font-medium transition-colors',
                tab === entry.key
                  ? 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                  : 'text-[var(--color-body)] hover:bg-[var(--color-muted)]',
              )}
            >
              <Icon size={16} aria-hidden="true" />
              {entry.label}
            </button>
          );
        })}
      </div>

      {tab === 'benefits' && <BenefitsSection />}
      {tab === 'statistics' && <StatisticsSection />}
      {tab === 'testimonials' && <TestimonialsSection />}
      {tab === 'partners' && <PartnersSection />}
      {tab === 'faqs' && <FaqsSection />}
    </>
  );
}

// ===========================================================================
// Benefits
// ===========================================================================

interface BenefitTranslation {
  title: string;
  description: string | null;
}

interface Benefit {
  id: number;
  icon: string | null;
  iconMediaId: number | null;
  iconUrl: string | null;
  status: Status;
  sortOrder: number;
  translations: Record<string, BenefitTranslation>;
}

interface BenefitForm {
  id: number | null;
  icon: string;
  iconMediaId: number | null;
  iconUrl: string | null;
  status: Status;
  sortOrder: number;
  translations: Record<string, BenefitTranslation>;
}

const EMPTY_BENEFIT_FORM: BenefitForm = {
  id: null,
  icon: '',
  iconMediaId: null,
  iconUrl: null,
  status: 'ACTIVE',
  sortOrder: 0,
  translations: { az: { title: '', description: '' } },
};

function BenefitsSection() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const languages = useLanguages();
  const [form, setForm] = useState<BenefitForm | null>(null);
  const [activeLanguage, setActiveLanguage] = useState('az');
  const [pickerOpen, setPickerOpen] = useState(false);

  const list = useQuery({
    queryKey: ['admin', 'content', 'benefits'],
    queryFn: () => getData<Benefit[]>('/admin/benefits'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'content', 'benefits'] });

  const save = useMutation({
    mutationFn: (values: BenefitForm) => {
      const payload = {
        icon: values.icon || null,
        iconMediaId: values.iconMediaId,
        status: values.status,
        sortOrder: values.sortOrder,
        translations: values.translations,
      };
      return values.id
        ? putData<Benefit>(`/admin/benefits/${values.id}`, payload)
        : postData<Benefit>('/admin/benefits', payload);
    },
    onSuccess: () => {
      invalidate();
      toast.success('Yadda saxlanıldı');
      setForm(null);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/benefits/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (benefit?: Benefit) => {
    setActiveLanguage('az');
    if (!benefit) {
      setForm({ ...EMPTY_BENEFIT_FORM });
      return;
    }
    setForm({
      id: benefit.id,
      icon: benefit.icon ?? '',
      iconMediaId: benefit.iconMediaId,
      iconUrl: benefit.iconUrl,
      status: benefit.status,
      sortOrder: benefit.sortOrder,
      translations: benefit.translations,
    });
  };

  return (
    <Card
      title="Üstünlüklər"
      description="Ana səhifədəki 'niyə bizi seçməlisiniz' kartları"
      actions={
        <Button size="sm" icon={<Plus size={14} aria-hidden="true" />} onClick={() => openEditor()}>
          Yeni
        </Button>
      }
    >
      {list.isLoading ? (
        <InlineLoader />
      ) : (list.data ?? []).length === 0 ? (
        <EmptyState icon={<ShieldCheck size={30} aria-hidden="true" />} title="Üstünlük yoxdur" />
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {(list.data ?? []).map((benefit) => (
            <li key={benefit.id} className="flex items-center gap-3 py-3">
              {benefit.iconUrl ? (
                <img src={benefit.iconUrl} alt="" className="h-9 w-9 shrink-0 rounded object-cover" />
              ) : (
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-[var(--color-muted)] text-xs text-[var(--color-subtle)]">
                  {benefit.icon || '—'}
                </span>
              )}
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-[var(--color-heading)]">
                  {benefit.translations['az']?.title ||
                    benefit.translations[Object.keys(benefit.translations)[0] ?? '']?.title ||
                    '(başlıqsız)'}
                </span>
              </span>
              <Badge tone={STATUS_TONE[benefit.status]}>{STATUS_LABEL[benefit.status]}</Badge>
              <Button variant="ghost" size="sm" icon={<Pencil size={14} aria-hidden="true" />} onClick={() => openEditor(benefit)} />
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Silinsin?')) remove.mutate(benefit.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {form && (
        <div className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">{form.id ? 'Üstünlüyü redaktə et' : 'Yeni üstünlük'}</h3>
            <Button variant="ghost" size="sm" icon={<X size={14} aria-hidden="true" />} onClick={() => setForm(null)} />
          </div>

          <div className="space-y-3">
            <div>
              <LanguageTabs languages={languages} active={activeLanguage} onChange={setActiveLanguage} />
              <Field label="Başlıq" htmlFor="benefitTitle" required>
                <Input
                  id="benefitTitle"
                  value={form.translations[activeLanguage]?.title ?? ''}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      translations: {
                        ...form.translations,
                        [activeLanguage]: {
                          title: event.target.value,
                          description: form.translations[activeLanguage]?.description ?? '',
                        },
                      },
                    })
                  }
                />
              </Field>
            </div>

            <Field label="Açıqlama" htmlFor="benefitDesc">
              <Textarea
                id="benefitDesc"
                rows={2}
                value={form.translations[activeLanguage]?.description ?? ''}
                onChange={(event) =>
                  setForm({
                    ...form,
                    translations: {
                      ...form.translations,
                      [activeLanguage]: {
                        title: form.translations[activeLanguage]?.title ?? '',
                        description: event.target.value,
                      },
                    },
                  })
                }
              />
            </Field>

            <Field label="İkon adı (Lucide)" htmlFor="benefitIcon" hint="Məs: shield-check, truck, headset">
              <Input
                id="benefitIcon"
                value={form.icon}
                onChange={(event) => setForm({ ...form, icon: event.target.value })}
                placeholder="shield-check"
              />
            </Field>

            <Field label="Şəkil (istəyə bağlı)">
              <div className="flex items-center gap-3">
                {form.iconUrl && (
                  <img src={form.iconUrl} alt="" className="h-10 w-10 rounded object-cover" />
                )}
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<ImageIcon size={14} aria-hidden="true" />}
                  onClick={() => setPickerOpen(true)}
                >
                  {form.iconMediaId ? 'Dəyiş' : 'Seç'}
                </Button>
                {form.iconMediaId && (
                  <Button variant="ghost" size="sm" onClick={() => setForm({ ...form, iconMediaId: null, iconUrl: null })}>
                    Sil
                  </Button>
                )}
              </div>
            </Field>

            <StatusAndSort
              status={form.status}
              sortOrder={form.sortOrder}
              onStatusChange={(status) => setForm({ ...form, status })}
              onSortOrderChange={(sortOrder) => setForm({ ...form, sortOrder })}
            />

            <Button
              loading={save.isPending}
              disabled={!form.translations['az']?.title?.trim()}
              onClick={() => save.mutate(form)}
            >
              Yadda saxla
            </Button>
          </div>
        </div>
      )}

      <MediaPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={(files: PickedMedia[]) => {
          const file = files[0];
          if (file && form) {
            setForm({ ...form, iconMediaId: file.id, iconUrl: file.publicUrl });
          }
        }}
      />
    </Card>
  );
}

// ===========================================================================
// Statistics
// ===========================================================================

interface StatisticTranslation {
  label: string;
}

interface Statistic {
  id: number;
  icon: string | null;
  value: string;
  suffix: string | null;
  status: Status;
  sortOrder: number;
  translations: Record<string, StatisticTranslation>;
}

interface StatisticForm {
  id: number | null;
  icon: string;
  value: string;
  suffix: string;
  status: Status;
  sortOrder: number;
  translations: Record<string, StatisticTranslation>;
}

const EMPTY_STATISTIC_FORM: StatisticForm = {
  id: null,
  icon: '',
  value: '',
  suffix: '',
  status: 'ACTIVE',
  sortOrder: 0,
  translations: { az: { label: '' } },
};

function StatisticsSection() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const languages = useLanguages();
  const [form, setForm] = useState<StatisticForm | null>(null);
  const [activeLanguage, setActiveLanguage] = useState('az');

  const list = useQuery({
    queryKey: ['admin', 'content', 'statistics'],
    queryFn: () => getData<Statistic[]>('/admin/statistics'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'content', 'statistics'] });

  const save = useMutation({
    mutationFn: (values: StatisticForm) => {
      const payload = {
        icon: values.icon || null,
        value: values.value,
        suffix: values.suffix || null,
        status: values.status,
        sortOrder: values.sortOrder,
        translations: values.translations,
      };
      return values.id
        ? putData<Statistic>(`/admin/statistics/${values.id}`, payload)
        : postData<Statistic>('/admin/statistics', payload);
    },
    onSuccess: () => {
      invalidate();
      toast.success('Yadda saxlanıldı');
      setForm(null);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/statistics/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (statistic?: Statistic) => {
    setActiveLanguage('az');
    if (!statistic) {
      setForm({ ...EMPTY_STATISTIC_FORM });
      return;
    }
    setForm({
      id: statistic.id,
      icon: statistic.icon ?? '',
      value: statistic.value,
      suffix: statistic.suffix ?? '',
      status: statistic.status,
      sortOrder: statistic.sortOrder,
      translations: statistic.translations,
    });
  };

  return (
    <Card
      title="Statistika"
      description="Say sayğacı kartları — məsələn '2 500+ müştəri'"
      actions={
        <Button size="sm" icon={<Plus size={14} aria-hidden="true" />} onClick={() => openEditor()}>
          Yeni
        </Button>
      }
    >
      {list.isLoading ? (
        <InlineLoader />
      ) : (list.data ?? []).length === 0 ? (
        <EmptyState icon={<BarChart3 size={30} aria-hidden="true" />} title="Statistika yoxdur" />
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {(list.data ?? []).map((statistic) => (
            <li key={statistic.id} className="flex items-center gap-3 py-3">
              <span className="font-display text-lg font-bold tabular">
                {statistic.value}
                {statistic.suffix}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm text-[var(--color-body)]">
                {statistic.translations['az']?.label ||
                  statistic.translations[Object.keys(statistic.translations)[0] ?? '']?.label ||
                  '(etiketsiz)'}
              </span>
              <Badge tone={STATUS_TONE[statistic.status]}>{STATUS_LABEL[statistic.status]}</Badge>
              <Button variant="ghost" size="sm" icon={<Pencil size={14} aria-hidden="true" />} onClick={() => openEditor(statistic)} />
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Silinsin?')) remove.mutate(statistic.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {form && (
        <div className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">{form.id ? 'Statistikanı redaktə et' : 'Yeni statistika'}</h3>
            <Button variant="ghost" size="sm" icon={<X size={14} aria-hidden="true" />} onClick={() => setForm(null)} />
          </div>

          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Dəyər" htmlFor="statValue" required hint="'2 500', '4.8' kimi mətn">
                <Input
                  id="statValue"
                  value={form.value}
                  onChange={(event) => setForm({ ...form, value: event.target.value })}
                  placeholder="2 500"
                />
              </Field>
              <Field label="Şəkilçi" htmlFor="statSuffix" hint="'+', '%'">
                <Input
                  id="statSuffix"
                  value={form.suffix}
                  onChange={(event) => setForm({ ...form, suffix: event.target.value })}
                  placeholder="+"
                />
              </Field>
            </div>

            <div>
              <LanguageTabs languages={languages} active={activeLanguage} onChange={setActiveLanguage} />
              <Field label="Etiket" htmlFor="statLabel" required>
                <Input
                  id="statLabel"
                  value={form.translations[activeLanguage]?.label ?? ''}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      translations: { ...form.translations, [activeLanguage]: { label: event.target.value } },
                    })
                  }
                  placeholder="Məmnun müştəri"
                />
              </Field>
            </div>

            <Field label="İkon adı (Lucide, istəyə bağlı)" htmlFor="statIcon">
              <Input id="statIcon" value={form.icon} onChange={(event) => setForm({ ...form, icon: event.target.value })} />
            </Field>

            <StatusAndSort
              status={form.status}
              sortOrder={form.sortOrder}
              onStatusChange={(status) => setForm({ ...form, status })}
              onSortOrderChange={(sortOrder) => setForm({ ...form, sortOrder })}
            />

            <Button
              loading={save.isPending}
              disabled={!form.value.trim() || !form.translations['az']?.label?.trim()}
              onClick={() => save.mutate(form)}
            >
              Yadda saxla
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

// ===========================================================================
// Testimonials
// ===========================================================================

interface TestimonialTranslation {
  content: string;
}

interface Testimonial {
  id: number;
  personName: string;
  position: string | null;
  companyName: string | null;
  photoMediaId: number | null;
  photoUrl: string | null;
  rating: number | null;
  featured: boolean;
  status: Status;
  sortOrder: number;
  translations: Record<string, TestimonialTranslation>;
}

interface TestimonialForm {
  id: number | null;
  personName: string;
  position: string;
  companyName: string;
  photoMediaId: number | null;
  photoUrl: string | null;
  rating: number | null;
  featured: boolean;
  status: Status;
  sortOrder: number;
  translations: Record<string, TestimonialTranslation>;
}

const EMPTY_TESTIMONIAL_FORM: TestimonialForm = {
  id: null,
  personName: '',
  position: '',
  companyName: '',
  photoMediaId: null,
  photoUrl: null,
  rating: 5,
  featured: false,
  status: 'ACTIVE',
  sortOrder: 0,
  translations: { az: { content: '' } },
};

function TestimonialsSection() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const languages = useLanguages();
  const [form, setForm] = useState<TestimonialForm | null>(null);
  const [activeLanguage, setActiveLanguage] = useState('az');
  const [pickerOpen, setPickerOpen] = useState(false);

  const list = useQuery({
    queryKey: ['admin', 'content', 'testimonials'],
    queryFn: () => getData<Testimonial[]>('/admin/testimonials'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'content', 'testimonials'] });

  const save = useMutation({
    mutationFn: (values: TestimonialForm) => {
      const payload = {
        personName: values.personName,
        position: values.position || null,
        companyName: values.companyName || null,
        photoMediaId: values.photoMediaId,
        rating: values.rating,
        featured: values.featured,
        status: values.status,
        sortOrder: values.sortOrder,
        translations: values.translations,
      };
      return values.id
        ? putData<Testimonial>(`/admin/testimonials/${values.id}`, payload)
        : postData<Testimonial>('/admin/testimonials', payload);
    },
    onSuccess: () => {
      invalidate();
      toast.success('Yadda saxlanıldı');
      setForm(null);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/testimonials/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (testimonial?: Testimonial) => {
    setActiveLanguage('az');
    if (!testimonial) {
      setForm({ ...EMPTY_TESTIMONIAL_FORM });
      return;
    }
    setForm({
      id: testimonial.id,
      personName: testimonial.personName,
      position: testimonial.position ?? '',
      companyName: testimonial.companyName ?? '',
      photoMediaId: testimonial.photoMediaId,
      photoUrl: testimonial.photoUrl,
      rating: testimonial.rating,
      featured: testimonial.featured,
      status: testimonial.status,
      sortOrder: testimonial.sortOrder,
      translations: testimonial.translations,
    });
  };

  return (
    <Card
      title="Müştəri rəyləri"
      actions={
        <Button size="sm" icon={<Plus size={14} aria-hidden="true" />} onClick={() => openEditor()}>
          Yeni
        </Button>
      }
    >
      {list.isLoading ? (
        <InlineLoader />
      ) : (list.data ?? []).length === 0 ? (
        <EmptyState icon={<Quote size={30} aria-hidden="true" />} title="Rəy yoxdur" />
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {(list.data ?? []).map((testimonial) => (
            <li key={testimonial.id} className="flex items-center gap-3 py-3">
              {testimonial.photoUrl ? (
                <img src={testimonial.photoUrl} alt="" className="h-9 w-9 shrink-0 rounded-full object-cover" />
              ) : (
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--color-muted)] text-xs font-semibold text-[var(--color-subtle)]">
                  {testimonial.personName.charAt(0)}
                </span>
              )}
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-[var(--color-heading)]">
                  {testimonial.personName}
                </span>
                {testimonial.companyName && (
                  <span className="block truncate text-xs text-[var(--color-subtle)]">{testimonial.companyName}</span>
                )}
              </span>
              {testimonial.rating != null && (
                <span className="flex items-center gap-0.5 text-xs text-[var(--color-warning)]">
                  <Star size={12} fill="currentColor" aria-hidden="true" />
                  {testimonial.rating}
                </span>
              )}
              {testimonial.featured && <Badge tone="info">Seçilmiş</Badge>}
              <Badge tone={STATUS_TONE[testimonial.status]}>{STATUS_LABEL[testimonial.status]}</Badge>
              <Button variant="ghost" size="sm" icon={<Pencil size={14} aria-hidden="true" />} onClick={() => openEditor(testimonial)} />
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Silinsin?')) remove.mutate(testimonial.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {form && (
        <div className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">{form.id ? 'Rəyi redaktə et' : 'Yeni rəy'}</h3>
            <Button variant="ghost" size="sm" icon={<X size={14} aria-hidden="true" />} onClick={() => setForm(null)} />
          </div>

          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Ad soyad" htmlFor="testPerson" required>
                <Input
                  id="testPerson"
                  value={form.personName}
                  onChange={(event) => setForm({ ...form, personName: event.target.value })}
                />
              </Field>
              <Field label="Vəzifə" htmlFor="testPosition">
                <Input
                  id="testPosition"
                  value={form.position}
                  onChange={(event) => setForm({ ...form, position: event.target.value })}
                />
              </Field>
              <Field label="Şirkət" htmlFor="testCompany">
                <Input
                  id="testCompany"
                  value={form.companyName}
                  onChange={(event) => setForm({ ...form, companyName: event.target.value })}
                />
              </Field>
              <Field label="Reytinq (1-5)" htmlFor="testRating">
                <Input
                  id="testRating"
                  type="number"
                  min={1}
                  max={5}
                  value={form.rating ?? ''}
                  onChange={(event) =>
                    setForm({ ...form, rating: event.target.value ? Number(event.target.value) : null })
                  }
                />
              </Field>
            </div>

            <div>
              <LanguageTabs languages={languages} active={activeLanguage} onChange={setActiveLanguage} />
              <Field label="Rəy mətni" htmlFor="testContent" required>
                <Textarea
                  id="testContent"
                  rows={3}
                  value={form.translations[activeLanguage]?.content ?? ''}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      translations: { ...form.translations, [activeLanguage]: { content: event.target.value } },
                    })
                  }
                />
              </Field>
            </div>

            <Field label="Şəkil">
              <div className="flex items-center gap-3">
                {form.photoUrl && (
                  <img src={form.photoUrl} alt="" className="h-10 w-10 rounded-full object-cover" />
                )}
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<ImageIcon size={14} aria-hidden="true" />}
                  onClick={() => setPickerOpen(true)}
                >
                  {form.photoMediaId ? 'Dəyiş' : 'Seç'}
                </Button>
                {form.photoMediaId && (
                  <Button variant="ghost" size="sm" onClick={() => setForm({ ...form, photoMediaId: null, photoUrl: null })}>
                    Sil
                  </Button>
                )}
              </div>
            </Field>

            <Toggle
              label="Seçilmiş"
              hint="Bölmədə əvvəlcə göstərilir"
              checked={form.featured}
              onChange={(featured) => setForm({ ...form, featured })}
            />

            <StatusAndSort
              status={form.status}
              sortOrder={form.sortOrder}
              onStatusChange={(status) => setForm({ ...form, status })}
              onSortOrderChange={(sortOrder) => setForm({ ...form, sortOrder })}
            />

            <Button
              loading={save.isPending}
              disabled={!form.personName.trim() || !form.translations['az']?.content?.trim()}
              onClick={() => save.mutate(form)}
            >
              Yadda saxla
            </Button>
          </div>
        </div>
      )}

      <MediaPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={(files: PickedMedia[]) => {
          const file = files[0];
          if (file && form) {
            setForm({ ...form, photoMediaId: file.id, photoUrl: file.publicUrl });
          }
        }}
      />
    </Card>
  );
}

// ===========================================================================
// Partners
// ===========================================================================

interface Partner {
  id: number;
  name: string;
  logoMediaId: number | null;
  logoUrl: string | null;
  websiteUrl: string | null;
  status: Status;
  sortOrder: number;
}

interface PartnerForm {
  id: number | null;
  name: string;
  logoMediaId: number | null;
  logoUrl: string | null;
  websiteUrl: string;
  status: Status;
  sortOrder: number;
}

const EMPTY_PARTNER_FORM: PartnerForm = {
  id: null,
  name: '',
  logoMediaId: null,
  logoUrl: null,
  websiteUrl: '',
  status: 'ACTIVE',
  sortOrder: 0,
};

function PartnersSection() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<PartnerForm | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const list = useQuery({
    queryKey: ['admin', 'content', 'partners'],
    queryFn: () => getData<Partner[]>('/admin/partners'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'content', 'partners'] });

  const save = useMutation({
    mutationFn: (values: PartnerForm) => {
      const payload = {
        name: values.name,
        logoMediaId: values.logoMediaId,
        websiteUrl: values.websiteUrl || null,
        status: values.status,
        sortOrder: values.sortOrder,
      };
      return values.id
        ? putData<Partner>(`/admin/partners/${values.id}`, payload)
        : postData<Partner>('/admin/partners', payload);
    },
    onSuccess: () => {
      invalidate();
      toast.success('Yadda saxlanıldı');
      setForm(null);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/partners/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (partner?: Partner) => {
    if (!partner) {
      setForm({ ...EMPTY_PARTNER_FORM });
      return;
    }
    setForm({
      id: partner.id,
      name: partner.name,
      logoMediaId: partner.logoMediaId,
      logoUrl: partner.logoUrl,
      websiteUrl: partner.websiteUrl ?? '',
      status: partner.status,
      sortOrder: partner.sortOrder,
    });
  };

  return (
    <Card
      title="Partnyorlar"
      description="Loqo zolağı"
      actions={
        <Button size="sm" icon={<Plus size={14} aria-hidden="true" />} onClick={() => openEditor()}>
          Yeni
        </Button>
      }
    >
      {list.isLoading ? (
        <InlineLoader />
      ) : (list.data ?? []).length === 0 ? (
        <EmptyState icon={<Building2 size={30} aria-hidden="true" />} title="Partnyor yoxdur" />
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {(list.data ?? []).map((partner) => (
            <li key={partner.id} className="flex items-center gap-3 py-3">
              {partner.logoUrl ? (
                <img src={partner.logoUrl} alt="" className="h-9 w-9 shrink-0 rounded object-contain" />
              ) : (
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-[var(--color-muted)] text-[var(--color-subtle)]">
                  <Building2 size={16} aria-hidden="true" />
                </span>
              )}
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-[var(--color-heading)]">
                {partner.name}
              </span>
              <Badge tone={STATUS_TONE[partner.status]}>{STATUS_LABEL[partner.status]}</Badge>
              <Button variant="ghost" size="sm" icon={<Pencil size={14} aria-hidden="true" />} onClick={() => openEditor(partner)} />
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Silinsin?')) remove.mutate(partner.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {form && (
        <div className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">{form.id ? 'Partnyoru redaktə et' : 'Yeni partnyor'}</h3>
            <Button variant="ghost" size="sm" icon={<X size={14} aria-hidden="true" />} onClick={() => setForm(null)} />
          </div>

          <div className="space-y-3">
            <Field label="Ad" htmlFor="partnerName" required>
              <Input id="partnerName" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </Field>

            <Field label="Veb sayt" htmlFor="partnerUrl">
              <Input
                id="partnerUrl"
                value={form.websiteUrl}
                onChange={(event) => setForm({ ...form, websiteUrl: event.target.value })}
                placeholder="https://…"
              />
            </Field>

            <Field label="Loqo">
              <div className="flex items-center gap-3">
                {form.logoUrl && <img src={form.logoUrl} alt="" className="h-10 w-10 rounded object-contain" />}
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<ImageIcon size={14} aria-hidden="true" />}
                  onClick={() => setPickerOpen(true)}
                >
                  {form.logoMediaId ? 'Dəyiş' : 'Seç'}
                </Button>
                {form.logoMediaId && (
                  <Button variant="ghost" size="sm" onClick={() => setForm({ ...form, logoMediaId: null, logoUrl: null })}>
                    Sil
                  </Button>
                )}
              </div>
            </Field>

            <StatusAndSort
              status={form.status}
              sortOrder={form.sortOrder}
              onStatusChange={(status) => setForm({ ...form, status })}
              onSortOrderChange={(sortOrder) => setForm({ ...form, sortOrder })}
            />

            <Button loading={save.isPending} disabled={!form.name.trim()} onClick={() => save.mutate(form)}>
              Yadda saxla
            </Button>
          </div>
        </div>
      )}

      <MediaPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={(files: PickedMedia[]) => {
          const file = files[0];
          if (file && form) {
            setForm({ ...form, logoMediaId: file.id, logoUrl: file.publicUrl });
          }
        }}
      />
    </Card>
  );
}

// ===========================================================================
// FAQ
// ===========================================================================

interface FaqTranslation {
  question: string;
  answer: string;
}

interface Faq {
  id: number;
  pageId: number | null;
  category: string | null;
  status: Status;
  sortOrder: number;
  translations: Record<string, FaqTranslation>;
}

interface FaqForm {
  id: number | null;
  category: string;
  status: Status;
  sortOrder: number;
  translations: Record<string, FaqTranslation>;
}

const EMPTY_FAQ_FORM: FaqForm = {
  id: null,
  category: '',
  status: 'ACTIVE',
  sortOrder: 0,
  translations: { az: { question: '', answer: '' } },
};

function FaqsSection() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const languages = useLanguages();
  const [form, setForm] = useState<FaqForm | null>(null);
  const [activeLanguage, setActiveLanguage] = useState('az');

  const list = useQuery({
    queryKey: ['admin', 'content', 'faqs'],
    queryFn: () => getData<Faq[]>('/admin/faqs'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'content', 'faqs'] });

  const save = useMutation({
    mutationFn: (values: FaqForm) => {
      const payload = {
        pageId: null,
        category: values.category || null,
        status: values.status,
        sortOrder: values.sortOrder,
        translations: values.translations,
      };
      return values.id
        ? putData<Faq>(`/admin/faqs/${values.id}`, payload)
        : postData<Faq>('/admin/faqs', payload);
    },
    onSuccess: () => {
      invalidate();
      toast.success('Yadda saxlanıldı');
      setForm(null);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/faqs/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (faq?: Faq) => {
    setActiveLanguage('az');
    if (!faq) {
      setForm({ ...EMPTY_FAQ_FORM });
      return;
    }
    setForm({
      id: faq.id,
      category: faq.category ?? '',
      status: faq.status,
      sortOrder: faq.sortOrder,
      translations: faq.translations,
    });
  };

  return (
    <Card
      title="Tez-tez verilən suallar"
      actions={
        <Button size="sm" icon={<Plus size={14} aria-hidden="true" />} onClick={() => openEditor()}>
          Yeni
        </Button>
      }
    >
      {list.isLoading ? (
        <InlineLoader />
      ) : (list.data ?? []).length === 0 ? (
        <EmptyState icon={<HelpCircle size={30} aria-hidden="true" />} title="Sual yoxdur" />
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {(list.data ?? []).map((faq) => (
            <li key={faq.id} className="flex items-center gap-3 py-3">
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-[var(--color-heading)]">
                {faq.translations['az']?.question ||
                  faq.translations[Object.keys(faq.translations)[0] ?? '']?.question ||
                  '(sualsız)'}
              </span>
              {faq.category && <Badge tone="neutral">{faq.category}</Badge>}
              <Badge tone={STATUS_TONE[faq.status]}>{STATUS_LABEL[faq.status]}</Badge>
              <Button variant="ghost" size="sm" icon={<Pencil size={14} aria-hidden="true" />} onClick={() => openEditor(faq)} />
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Silinsin?')) remove.mutate(faq.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {form && (
        <div className="mt-5 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-muted)]/40 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">{form.id ? 'Sualı redaktə et' : 'Yeni sual'}</h3>
            <Button variant="ghost" size="sm" icon={<X size={14} aria-hidden="true" />} onClick={() => setForm(null)} />
          </div>

          <div className="space-y-3">
            <div>
              <LanguageTabs languages={languages} active={activeLanguage} onChange={setActiveLanguage} />
              <Field label="Sual" htmlFor="faqQuestion" required>
                <Input
                  id="faqQuestion"
                  value={form.translations[activeLanguage]?.question ?? ''}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      translations: {
                        ...form.translations,
                        [activeLanguage]: {
                          question: event.target.value,
                          answer: form.translations[activeLanguage]?.answer ?? '',
                        },
                      },
                    })
                  }
                />
              </Field>
            </div>

            <Field label="Cavab" htmlFor="faqAnswer" required>
              <Textarea
                id="faqAnswer"
                rows={3}
                value={form.translations[activeLanguage]?.answer ?? ''}
                onChange={(event) =>
                  setForm({
                    ...form,
                    translations: {
                      ...form.translations,
                      [activeLanguage]: {
                        question: form.translations[activeLanguage]?.question ?? '',
                        answer: event.target.value,
                      },
                    },
                  })
                }
              />
            </Field>

            <Field label="Kateqoriya (istəyə bağlı)" htmlFor="faqCategory" hint="Sualları qruplaşdırmaq üçün">
              <Input
                id="faqCategory"
                value={form.category}
                onChange={(event) => setForm({ ...form, category: event.target.value })}
                placeholder="Çatdırılma"
              />
            </Field>

            <StatusAndSort
              status={form.status}
              sortOrder={form.sortOrder}
              onStatusChange={(status) => setForm({ ...form, status })}
              onSortOrderChange={(sortOrder) => setForm({ ...form, sortOrder })}
            />

            <Button
              loading={save.isPending}
              disabled={!form.translations['az']?.question?.trim() || !form.translations['az']?.answer?.trim()}
              onClick={() => save.mutate(form)}
            >
              Yadda saxla
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
