import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Copy,
  Eye,
  EyeOff,
  ImagePlus,
  Plus,
  Rocket,
  Save,
  Settings2,
  Trash2,
  X,
} from 'lucide-react';
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
import { MediaPicker } from '../components/MediaPicker';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, patchData, postData, putData } from '@/lib/api';
import type { Language } from '@/types/api';

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

type SectionField =
  | 'EYEBROW' | 'TITLE' | 'SUBTITLE' | 'CONTENT'
  | 'MEDIA' | 'BACKGROUND_MEDIA' | 'BUTTON' | 'SECONDARY_BUTTON';

type BackgroundStyle = 'DEFAULT' | 'MUTED' | 'DARK' | 'ACCENT' | 'IMAGE';

interface SectionTypeInfo {
  type: string;
  label: string;
  description: string;
  fields: SectionField[];
  variants: string[];
}

interface Section {
  id: number;
  sectionType: string;
  sectionLabel: string;
  variant: string;
  availableVariants: string[];
  usedFields: SectionField[];
  eyebrow: string | null;
  title: string | null;
  subtitle: string | null;
  content: string | null;
  mediaId: number | null;
  mediaUrl: string | null;
  backgroundMediaId: number | null;
  backgroundMediaUrl: string | null;
  buttonText: string | null;
  buttonUrl: string | null;
  secondaryButtonText: string | null;
  secondaryButtonUrl: string | null;
  backgroundStyle: BackgroundStyle;
  configuration: Record<string, unknown>;
  sortOrder: number;
  active: boolean;
}

interface PageVersion {
  id: number;
  pageId: number;
  pageCode: string;
  versionNumber: number;
  languageCode: string;
  slug: string;
  title: string;
  shortDescription: string | null;
  coverMediaId: number | null;
  coverUrl: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  seoKeywords: string | null;
  ogMediaId: number | null;
  canonicalUrl: string | null;
  allowIndexing: boolean;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedAt: string | null;
  sections: Section[];
}

const BACKGROUND_LABELS: Record<BackgroundStyle, string> = {
  DEFAULT: 'Standart',
  MUTED: 'Solğun fon',
  DARK: 'Tünd',
  ACCENT: 'Aksent rəngi',
  IMAGE: 'Şəkil fonu',
};

/**
 * Page editor.
 *
 * Structure mirrors how the page is actually built: a vertical list of sections on the
 * left in the order visitors will read them, and one section's fields on the right.
 * Editing a section inline in the list was tried mentally and rejected — a HERO with six
 * fields would push everything below it off screen and destroy the sense of order.
 *
 * Everything here edits a DRAFT. The live page changes only on publish.
 */
export function PageEditorPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [language, setLanguage] = useState('az');
  const [selectedSectionId, setSelectedSectionId] = useState<number | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });

  const registry = useQuery({
    queryKey: ['admin', 'section-types'],
    queryFn: () => getData<SectionTypeInfo[]>('/admin/pages/section-types'),
    staleTime: 60 * 60 * 1000,
  });

  const version = useQuery({
    queryKey: ['admin', 'page', id, 'editable', language],
    queryFn: () =>
      getData<PageVersion>(`/admin/pages/${id}/editable`, undefined).catch(() => null),
    enabled: Boolean(id),
    retry: false,
  });

  // The language tab is sent as Accept-Language, so switching it refetches.
  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'page', id, 'editable'] });
  }, [language, id, queryClient]);

  const data = version.data;
  const isDraft = data?.status === 'DRAFT';

  const refresh = (fresh: PageVersion) => {
    queryClient.setQueryData(['admin', 'page', id, 'editable', language], fresh);
  };

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'pages'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'page', id] });
  };

  // --- mutations ----------------------------------------------------------

  const createDraft = useMutation({
    mutationFn: () =>
      postData<PageVersion>(`/admin/pages/${id}/versions`, { languageCode: language }),
    onSuccess: (fresh) => {
      refresh(fresh);
      invalidateAll();
      toast.success('Draft açıldı — indi redaktə edə bilərsiniz');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const publish = useMutation({
    mutationFn: () => postData<PageVersion>(`/admin/pages/versions/${data!.id}/publish`),
    onSuccess: (fresh) => {
      refresh(fresh);
      invalidateAll();
      toast.success('Səhifə dərc edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const discardDraft = useMutation({
    mutationFn: () => deleteRequest(`/admin/pages/versions/${data!.id}`),
    onSuccess: () => {
      invalidateAll();
      version.refetch();
      toast.success('Draft ləğv edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const saveSettings = useMutation({
    mutationFn: (values: Partial<PageVersion>) =>
      putData<PageVersion>(`/admin/pages/versions/${data!.id}`, {
        title: values.title,
        slug: values.slug,
        shortDescription: values.shortDescription,
        coverMediaId: values.coverMediaId,
        seoTitle: values.seoTitle,
        seoDescription: values.seoDescription,
        seoKeywords: values.seoKeywords,
        ogMediaId: values.ogMediaId,
        canonicalUrl: values.canonicalUrl,
        allowIndexing: values.allowIndexing,
      }),
    onSuccess: (fresh) => {
      refresh(fresh);
      toast.success('Səhifə parametrləri yeniləndi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const addSection = useMutation({
    mutationFn: (sectionType: string) =>
      postData<PageVersion>(`/admin/pages/versions/${data!.id}/sections`, {
        sectionType,
        title: registry.data?.find((entry) => entry.type === sectionType)?.label ?? '',
      }),
    onSuccess: (fresh) => {
      refresh(fresh);
      setPaletteOpen(false);
      // Select what was just added: the next action is always to fill it in.
      const added = fresh.sections[fresh.sections.length - 1];
      if (added) {
        setSelectedSectionId(added.id);
      }
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const updateSection = useMutation({
    mutationFn: (section: Section) =>
      putData<PageVersion>(`/admin/pages/versions/${data!.id}/sections/${section.id}`, {
        sectionType: section.sectionType,
        variant: section.variant,
        eyebrow: section.eyebrow,
        title: section.title,
        subtitle: section.subtitle,
        content: section.content,
        mediaId: section.mediaId,
        backgroundMediaId: section.backgroundMediaId,
        buttonText: section.buttonText,
        buttonUrl: section.buttonUrl,
        secondaryButtonText: section.secondaryButtonText,
        secondaryButtonUrl: section.secondaryButtonUrl,
        backgroundStyle: section.backgroundStyle,
        configuration: section.configuration,
        active: section.active,
      }),
    onSuccess: (fresh) => {
      refresh(fresh);
      toast.success('Bölmə yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const sectionAction = useMutation({
    mutationFn: async ({ action, sectionId, active }: {
      action: 'delete' | 'duplicate' | 'toggle' | 'move';
      sectionId: number;
      active?: boolean;
      items?: { id: number; sortOrder: number }[];
    }) => {
      const base = `/admin/pages/versions/${data!.id}/sections`;
      switch (action) {
        case 'delete':
          return deleteRequest(`${base}/${sectionId}`).then(() =>
            getData<PageVersion>(`/admin/pages/versions/${data!.id}`),
          );
        case 'duplicate':
          return postData<PageVersion>(`${base}/${sectionId}/duplicate`);
        case 'toggle':
          return patchData<PageVersion>(`${base}/${sectionId}/status`, { active });
        default:
          throw new Error('unsupported');
      }
    },
    onSuccess: (fresh) => refresh(fresh as PageVersion),
    onError: (error: ApiError) => toast.error(error.message),
  });

  const reorder = useMutation({
    mutationFn: (items: { id: number; sortOrder: number }[]) =>
      putData<PageVersion>(`/admin/pages/versions/${data!.id}/sections/reorder`, { items }),
    onSuccess: (fresh) => refresh(fresh),
    onError: (error: ApiError) => toast.error(error.message),
  });

  /** Moves a section one slot up or down and sends the whole new order. */
  const move = (sectionId: number, direction: -1 | 1) => {
    if (!data) return;
    const ordered = [...data.sections].sort((a, b) => a.sortOrder - b.sortOrder);
    const index = ordered.findIndex((section) => section.id === sectionId);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= ordered.length) return;

    [ordered[index], ordered[target]] = [ordered[target]!, ordered[index]!];
    reorder.mutate(ordered.map((section, position) => ({ id: section.id, sortOrder: position })));
  };

  // --- render -------------------------------------------------------------

  if (version.isLoading) {
    return <InlineLoader label="Səhifə yüklənir…" />;
  }

  const selected = data?.sections.find((section) => section.id === selectedSectionId) ?? null;
  const activeLanguages = languages.data ?? [];

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link
            to="/admin/pages"
            className="inline-flex items-center gap-1.5 hover:text-[var(--color-heading)]"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            Səhifələr
          </Link>
        }
        title={data?.title ?? 'Səhifə'}
        description={data ? `/${data.slug} · v${data.versionNumber}` : undefined}
        actions={
          <>
            {activeLanguages.length > 1 && (
              <div className="flex gap-1">
                {activeLanguages.map((entry) => (
                  <button
                    key={entry.code}
                    type="button"
                    onClick={() => setLanguage(entry.code)}
                    className={clsx(
                      'rounded-[var(--radius-button)] px-3 py-1.5 text-xs font-semibold uppercase transition-colors',
                      language === entry.code
                        ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                        : 'text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                    )}
                  >
                    {entry.code}
                  </button>
                ))}
              </div>
            )}

            {data && (
              <Button
                variant="secondary"
                icon={<Settings2 size={15} aria-hidden="true" />}
                onClick={() => setSettingsOpen((open) => !open)}
              >
                Parametrlər
              </Button>
            )}

            {data && !isDraft && (
              <Button loading={createDraft.isPending} onClick={() => createDraft.mutate()}>
                Redaktə et
              </Button>
            )}

            {data && isDraft && (
              <Button
                loading={publish.isPending}
                icon={<Rocket size={15} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Dəyişikliklər saytda dərhal görünəcək. Davam edilsin?')) {
                    publish.mutate();
                  }
                }}
              >
                Dərc et
              </Button>
            )}
          </>
        }
      />

      {!data ? (
        <Card>
          <div className="py-10 text-center">
            <p className="mb-4 text-sm text-[var(--color-body)]">
              Bu dil ({language.toUpperCase()}) üçün hələ versiya yoxdur.
            </p>
            <Button loading={createDraft.isPending} onClick={() => createDraft.mutate()}>
              Bu dil üçün versiya yarat
            </Button>
          </div>
        </Card>
      ) : (
        <>
          {/* Status banner: an operator must never be unsure whether what they see is live. */}
          <div
            className={clsx(
              'mb-6 flex flex-wrap items-center gap-3 rounded-[var(--radius-card)] border px-4 py-3 text-sm',
              isDraft
                ? 'border-[var(--color-warning)]/30 bg-[var(--color-warning)]/8'
                : 'border-[var(--color-success)]/30 bg-[var(--color-success)]/8',
            )}
          >
            <Badge tone={isDraft ? 'warning' : 'success'}>
              {isDraft ? 'Qaralama' : 'Canlı'}
            </Badge>
            <span className="flex-1 text-[var(--color-body)]">
              {isDraft
                ? 'Dəyişiklikləriniz saytda hələ görünmür. Hazır olduqda “Dərc et” düyməsinə basın.'
                : 'Bu, saytda görünən versiyadır. Dəyişmək üçün “Redaktə et” düyməsinə basın.'}
            </span>
            {isDraft && (
              <Button
                variant="ghost"
                size="sm"
                loading={discardDraft.isPending}
                onClick={() => {
                  if (window.confirm('Qaralama silinsin? Dəyişikliklər itiriləcək.')) {
                    discardDraft.mutate();
                  }
                }}
              >
                Qaralamanı ləğv et
              </Button>
            )}
            <a
              href={`/${data.slug}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm font-medium text-[var(--color-primary)] hover:underline"
            >
              Saytda bax
            </a>
          </div>

          {settingsOpen && (
            <PageSettingsCard
              version={data}
              editable={isDraft}
              pending={saveSettings.isPending}
              onClose={() => setSettingsOpen(false)}
              onSave={(values) => saveSettings.mutate(values)}
            />
          )}

          <div className="grid gap-6 lg:grid-cols-[1fr_400px]">
            {/* ---------------- section list ---------------- */}
            <Card
              title="Bölmələr"
              description="Ziyarətçilərin görəcəyi ardıcıllıqla"
              bodyClassName="p-0"
              actions={
                isDraft && (
                  <Button
                    size="sm"
                    icon={<Plus size={14} aria-hidden="true" />}
                    onClick={() => setPaletteOpen(true)}
                  >
                    Bölmə əlavə et
                  </Button>
                )
              }
            >
              {data.sections.length === 0 ? (
                <div className="px-5 py-14 text-center">
                  <p className="mb-4 text-sm text-[var(--color-subtle)]">
                    Bu səhifədə hələ bölmə yoxdur.
                  </p>
                  {isDraft && (
                    <Button
                      icon={<Plus size={15} aria-hidden="true" />}
                      onClick={() => setPaletteOpen(true)}
                    >
                      İlk bölməni əlavə et
                    </Button>
                  )}
                </div>
              ) : (
                <ul className="divide-y divide-[var(--color-border)]">
                  {[...data.sections]
                    .sort((a, b) => a.sortOrder - b.sortOrder)
                    .map((section, index, all) => (
                      <li key={section.id}>
                        <div
                          className={clsx(
                            'flex items-center gap-3 px-4 py-3 transition-colors',
                            selectedSectionId === section.id
                              ? 'bg-[var(--color-primary)]/6'
                              : 'hover:bg-[var(--color-muted)]',
                            !section.active && 'opacity-55',
                          )}
                        >
                          <span className="flex w-6 shrink-0 flex-col">
                            <button
                              type="button"
                              disabled={!isDraft || index === 0}
                              onClick={() => move(section.id, -1)}
                              aria-label="Yuxarı"
                              className="text-[var(--color-subtle)] hover:text-[var(--color-heading)] disabled:opacity-25"
                            >
                              <ChevronUp size={14} aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              disabled={!isDraft || index === all.length - 1}
                              onClick={() => move(section.id, 1)}
                              aria-label="Aşağı"
                              className="text-[var(--color-subtle)] hover:text-[var(--color-heading)] disabled:opacity-25"
                            >
                              <ChevronDown size={14} aria-hidden="true" />
                            </button>
                          </span>

                          <button
                            type="button"
                            onClick={() => setSelectedSectionId(section.id)}
                            className="min-w-0 flex-1 text-start"
                          >
                            <span className="block text-sm font-medium text-[var(--color-heading)]">
                              {section.sectionLabel}
                              {section.variant !== 'DEFAULT' && (
                                <span className="ms-1.5 text-xs font-normal text-[var(--color-subtle)]">
                                  {section.variant}
                                </span>
                              )}
                            </span>
                            <span className="block truncate text-xs text-[var(--color-subtle)]">
                              {section.title || section.subtitle || '— məzmun yoxdur —'}
                            </span>
                          </button>

                          {!section.active && <Badge tone="neutral">Gizli</Badge>}

                          {isDraft && (
                            <div className="flex shrink-0 items-center gap-0.5">
                              <Button
                                variant="ghost"
                                size="sm"
                                title={section.active ? 'Gizlət' : 'Göstər'}
                                onClick={() =>
                                  sectionAction.mutate({
                                    action: 'toggle',
                                    sectionId: section.id,
                                    active: !section.active,
                                  })
                                }
                                icon={
                                  section.active ? (
                                    <Eye size={14} aria-hidden="true" />
                                  ) : (
                                    <EyeOff size={14} aria-hidden="true" />
                                  )
                                }
                              />
                              <Button
                                variant="ghost"
                                size="sm"
                                title="Kopyala"
                                onClick={() =>
                                  sectionAction.mutate({ action: 'duplicate', sectionId: section.id })
                                }
                                icon={<Copy size={14} aria-hidden="true" />}
                              />
                              <Button
                                variant="ghost"
                                size="sm"
                                title="Sil"
                                className="text-[var(--color-danger)]"
                                onClick={() => {
                                  if (window.confirm('Bölmə silinsin?')) {
                                    sectionAction.mutate({
                                      action: 'delete',
                                      sectionId: section.id,
                                    });
                                    if (selectedSectionId === section.id) {
                                      setSelectedSectionId(null);
                                    }
                                  }
                                }}
                                icon={<Trash2 size={14} aria-hidden="true" />}
                              />
                            </div>
                          )}
                        </div>
                      </li>
                    ))}
                </ul>
              )}
            </Card>

            {/* ---------------- section editor ---------------- */}
            <div className="lg:sticky lg:top-24 lg:self-start">
              {selected ? (
                <SectionEditor
                  key={selected.id}
                  section={selected}
                  editable={isDraft}
                  pending={updateSection.isPending}
                  onSave={(next) => updateSection.mutate(next)}
                  onClose={() => setSelectedSectionId(null)}
                />
              ) : (
                <Card title="Bölmə seçin">
                  <p className="text-sm text-[var(--color-body)]">
                    Redaktə etmək üçün soldakı siyahıdan bölməyə klikləyin.
                  </p>
                  {!isDraft && (
                    <p className="mt-3 rounded-[var(--radius-button)] bg-[var(--color-muted)] px-3 py-2 text-xs text-[var(--color-subtle)]">
                      Dəyişiklik etmək üçün əvvəlcə “Redaktə et” düyməsi ilə qaralama açın.
                    </p>
                  )}
                </Card>
              )}
            </div>
          </div>
        </>
      )}

      {/* ---------------- section palette ---------------- */}
      {paletteOpen && registry.data && (
        <SectionPalette
          types={registry.data}
          pending={addSection.isPending}
          onPick={(type) => addSection.mutate(type)}
          onClose={() => setPaletteOpen(false)}
        />
      )}
    </>
  );
}

/**
 * The "add section" palette.
 *
 * A closed set of components with descriptions, not a blank canvas — this is exactly the
 * boundary the plan drew: the admin composes from ready sections, never places free
 * elements. Descriptions matter because "HERO" means nothing to a shop owner.
 */
function SectionPalette({
  types,
  pending,
  onPick,
  onClose,
}: {
  types: SectionTypeInfo[];
  pending: boolean;
  onPick: (type: string) => void;
  onClose: () => void;
}) {
  const [search, setSearch] = useState('');

  const filtered = types.filter(
    (type) =>
      !search ||
      type.label.toLowerCase().includes(search.toLowerCase()) ||
      type.description.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Bölmə seçimi"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="flex max-h-[80vh] w-full max-w-2xl flex-col overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface)] shadow-[var(--shadow-popover)]">
        <div className="flex items-center justify-between border-b border-[var(--color-border)] px-5 py-4">
          <h2 className="font-semibold text-[var(--color-heading)]">Bölmə əlavə et</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Bağla"
            className="rounded p-1 text-[var(--color-subtle)] hover:text-[var(--color-heading)]"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div className="border-b border-[var(--color-border)] px-5 py-3">
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Bölmə axtar…"
            autoFocus
          />
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          <div className="grid gap-2 sm:grid-cols-2">
            {filtered.map((type) => (
              <button
                key={type.type}
                type="button"
                disabled={pending}
                onClick={() => onPick(type.type)}
                className="rounded-[var(--radius-button)] border border-[var(--color-border)] p-3 text-start transition-colors hover:border-[var(--color-primary)] hover:bg-[var(--color-primary)]/5 disabled:opacity-50"
              >
                <span className="block text-sm font-medium text-[var(--color-heading)]">
                  {type.label}
                </span>
                <span className="mt-0.5 block text-xs text-[var(--color-subtle)]">
                  {type.description}
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/** Renders only the inputs the chosen section type actually declares. */
function SectionEditor({
  section,
  editable,
  pending,
  onSave,
  onClose,
}: {
  section: Section;
  editable: boolean;
  pending: boolean;
  onSave: (section: Section) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<Section>(section);
  const [pickerFor, setPickerFor] = useState<
    { target: 'media' | 'background'; kind: 'IMAGE' | 'VIDEO' } | null
  >(null);

  const uses = (field: SectionField) => draft.usedFields.includes(field);
  const patch = (changes: Partial<Section>) => setDraft({ ...draft, ...changes });

  return (
    <>
      <Card
        title={draft.sectionLabel}
        actions={
          <Button variant="ghost" size="sm" onClick={onClose}>
            Bağla
          </Button>
        }
      >
        <fieldset disabled={!editable} className="space-y-4">
          {draft.availableVariants.length > 1 && (
            <Field label="Görünüş variantı" htmlFor="variant">
              <Select
                id="variant"
                value={draft.variant}
                onChange={(event) => patch({ variant: event.target.value })}
              >
                {draft.availableVariants.map((variant) => (
                  <option key={variant} value={variant}>
                    {variant}
                  </option>
                ))}
              </Select>
            </Field>
          )}

          {uses('EYEBROW') && (
            <Field label="Üst yazı" htmlFor="eyebrow" hint="Başlığın üstündəki kiçik mətn">
              <Input
                id="eyebrow"
                value={draft.eyebrow ?? ''}
                onChange={(event) => patch({ eyebrow: event.target.value })}
              />
            </Field>
          )}

          {uses('TITLE') && (
            <Field label="Başlıq" htmlFor="sectionTitle">
              <Input
                id="sectionTitle"
                value={draft.title ?? ''}
                onChange={(event) => patch({ title: event.target.value })}
              />
            </Field>
          )}

          {uses('SUBTITLE') && (
            <Field label="Alt başlıq" htmlFor="subtitle">
              <Textarea
                id="subtitle"
                rows={2}
                value={draft.subtitle ?? ''}
                onChange={(event) => patch({ subtitle: event.target.value })}
              />
            </Field>
          )}

          {uses('CONTENT') && (
            <Field
              label="Məzmun"
              htmlFor="content"
              hint="HTML dəstəklənir və serverdə təmizlənir"
            >
              <Textarea
                id="content"
                rows={7}
                value={draft.content ?? ''}
                onChange={(event) => patch({ content: event.target.value })}
              />
            </Field>
          )}

          {uses('MEDIA') && (
            <Field label="Şəkil / video">
              <MediaSlot
                url={draft.mediaUrl}
                disabled={!editable}
                onPick={() => setPickerFor({ target: 'media', kind: 'IMAGE' })}
                onPickVideo={() => setPickerFor({ target: 'media', kind: 'VIDEO' })}
                onClear={() => patch({ mediaId: null, mediaUrl: null })}
              />
            </Field>
          )}

          {uses('BACKGROUND_MEDIA') && (
            <Field label="Fon şəkli / video" hint="Video yalnız 'Şəkil' fon üslubu seçildikdə göstərilir">
              <MediaSlot
                url={draft.backgroundMediaUrl}
                disabled={!editable}
                onPick={() => setPickerFor({ target: 'background', kind: 'IMAGE' })}
                onPickVideo={() => setPickerFor({ target: 'background', kind: 'VIDEO' })}
                onClear={() => patch({ backgroundMediaId: null, backgroundMediaUrl: null })}
              />
            </Field>
          )}

          {uses('BUTTON') && (
            <div className="grid grid-cols-2 gap-3">
              <Field label="Düymə mətni" htmlFor="buttonText">
                <Input
                  id="buttonText"
                  value={draft.buttonText ?? ''}
                  onChange={(event) => patch({ buttonText: event.target.value })}
                />
              </Field>
              <Field label="Düymə linki" htmlFor="buttonUrl" hint="/kataloq və ya https://…">
                <Input
                  id="buttonUrl"
                  value={draft.buttonUrl ?? ''}
                  onChange={(event) => patch({ buttonUrl: event.target.value })}
                />
              </Field>
            </div>
          )}

          {uses('SECONDARY_BUTTON') && (
            <div className="grid grid-cols-2 gap-3">
              <Field label="İkinci düymə" htmlFor="secondaryButtonText">
                <Input
                  id="secondaryButtonText"
                  value={draft.secondaryButtonText ?? ''}
                  onChange={(event) => patch({ secondaryButtonText: event.target.value })}
                />
              </Field>
              <Field label="İkinci link" htmlFor="secondaryButtonUrl">
                <Input
                  id="secondaryButtonUrl"
                  value={draft.secondaryButtonUrl ?? ''}
                  onChange={(event) => patch({ secondaryButtonUrl: event.target.value })}
                />
              </Field>
            </div>
          )}

          <Field label="Fon stili" htmlFor="backgroundStyle">
            <Select
              id="backgroundStyle"
              value={draft.backgroundStyle}
              onChange={(event) =>
                patch({ backgroundStyle: event.target.value as BackgroundStyle })
              }
            >
              {(Object.keys(BACKGROUND_LABELS) as BackgroundStyle[]).map((style) => (
                <option key={style} value={style}>
                  {BACKGROUND_LABELS[style]}
                </option>
              ))}
            </Select>
          </Field>

          <Toggle
            label="Bölmə görünsün"
            hint="Söndürülsə bölmə saytda göstərilmir, amma silinmir"
            checked={draft.active}
            onChange={(checked) => patch({ active: checked })}
          />

          {/* Section-specific knobs (limit, showCountdown…) live in configuration. */}
          <Field
            label="Əlavə parametrlər (JSON)"
            htmlFor="configuration"
            hint='Məsələn: {"limit": 8, "showCountdown": true}'
          >
            <Textarea
              id="configuration"
              rows={3}
              className="font-mono text-xs"
              value={JSON.stringify(draft.configuration ?? {}, null, 2)}
              onChange={(event) => {
                try {
                  patch({ configuration: JSON.parse(event.target.value || '{}') });
                } catch {
                  // Ignore while the operator is mid-typing; the last valid value stands.
                }
              }}
            />
          </Field>
        </fieldset>

        {editable && (
          <Button
            className="mt-5 w-full"
            loading={pending}
            icon={<Save size={15} aria-hidden="true" />}
            onClick={() => onSave(draft)}
          >
            Bölməni yadda saxla
          </Button>
        )}
      </Card>

      <MediaPicker
        open={pickerFor !== null}
        type={pickerFor?.kind ?? 'IMAGE'}
        onClose={() => setPickerFor(null)}
        onSelect={(picked) => {
          const media = picked[0];
          if (!media) return;
          if (pickerFor?.target === 'media') {
            patch({ mediaId: media.id, mediaUrl: media.publicUrl });
          } else {
            patch({ backgroundMediaId: media.id, backgroundMediaUrl: media.publicUrl });
          }
        }}
      />
    </>
  );
}

/** No mime info travels with a bare URL in this editor state, so the extension is the
 * only signal available for choosing an `<img>` vs `<video>` preview. */
function looksLikeVideo(url: string): boolean {
  return /\.(mp4|webm|mov|ogg)(\?.*)?$/i.test(url);
}

function MediaSlot({
  url,
  disabled,
  onPick,
  onPickVideo,
  onClear,
}: {
  url: string | null;
  disabled: boolean;
  onPick: () => void;
  onPickVideo?: () => void;
  onClear: () => void;
}) {
  if (!url) {
    return (
      <div className="flex gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={disabled}
          className="flex-1"
          icon={<ImagePlus size={14} aria-hidden="true" />}
          onClick={onPick}
        >
          Şəkil seç
        </Button>
        {onPickVideo && (
          <Button variant="secondary" size="sm" disabled={disabled} className="flex-1" onClick={onPickVideo}>
            Video seç
          </Button>
        )}
      </div>
    );
  }

  const isVideo = looksLikeVideo(url);

  return (
    <div className="relative overflow-hidden rounded-[var(--radius-button)] border border-[var(--color-border)]">
      {isVideo ? (
        <video src={url} muted className="h-28 w-full object-cover" />
      ) : (
        <img src={url} alt="" className="h-28 w-full object-cover" />
      )}
      <div className="absolute inset-x-0 bottom-0 flex gap-1 bg-black/60 p-1.5">
        <button
          type="button"
          disabled={disabled}
          onClick={onPick}
          className="flex-1 rounded py-1 text-xs font-medium text-white hover:bg-white/20 disabled:opacity-50"
        >
          Dəyiş
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={onClear}
          className="flex-1 rounded py-1 text-xs font-medium text-white hover:bg-white/20 disabled:opacity-50"
        >
          Sil
        </button>
      </div>
    </div>
  );
}

function PageSettingsCard({
  version,
  editable,
  pending,
  onClose,
  onSave,
}: {
  version: PageVersion;
  editable: boolean;
  pending: boolean;
  onClose: () => void;
  onSave: (values: Partial<PageVersion>) => void;
}) {
  const [draft, setDraft] = useState(version);

  return (
    <Card
      title="Səhifə parametrləri"
      description={`${version.languageCode.toUpperCase()} dili üçün`}
      className="mb-6"
      actions={
        <Button variant="ghost" size="sm" onClick={onClose}>
          Bağla
        </Button>
      }
    >
      <fieldset disabled={!editable} className="grid gap-4 sm:grid-cols-2">
        <Field label="Başlıq" htmlFor="versionTitle" required>
          <Input
            id="versionTitle"
            value={draft.title}
            onChange={(event) => setDraft({ ...draft, title: event.target.value })}
          />
        </Field>

        <Field label="Slug" htmlFor="versionSlug" hint={`Ünvan: /${draft.slug}`}>
          <Input
            id="versionSlug"
            value={draft.slug}
            onChange={(event) => setDraft({ ...draft, slug: event.target.value })}
          />
        </Field>

        <Field label="Qısa təsvir" htmlFor="shortDescription" className="sm:col-span-2">
          <Textarea
            id="shortDescription"
            rows={2}
            value={draft.shortDescription ?? ''}
            onChange={(event) => setDraft({ ...draft, shortDescription: event.target.value })}
          />
        </Field>

        <Field label="SEO başlığı" htmlFor="seoTitle" hint="Boş qoyulsa səhifə başlığı istifadə olunur">
          <Input
            id="seoTitle"
            value={draft.seoTitle ?? ''}
            onChange={(event) => setDraft({ ...draft, seoTitle: event.target.value })}
            placeholder={draft.title}
          />
        </Field>

        <Field label="SEO təsviri" htmlFor="seoDescription">
          <Input
            id="seoDescription"
            value={draft.seoDescription ?? ''}
            onChange={(event) => setDraft({ ...draft, seoDescription: event.target.value })}
          />
        </Field>

        <div className="sm:col-span-2">
          <Toggle
            label="Axtarış sistemlərində göstərilsin"
            hint="Söndürülsə səhifəyə noindex tətbiq olunur"
            checked={draft.allowIndexing}
            onChange={(checked) => setDraft({ ...draft, allowIndexing: checked })}
          />
        </div>
      </fieldset>

      {editable && (
        <Button className="mt-5" loading={pending} onClick={() => onSave(draft)}>
          Yadda saxla
        </Button>
      )}
    </Card>
  );
}
