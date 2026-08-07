import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  Braces,
  Check,
  GripVertical,
  Hash,
  List,
  ListChecks,
  Lock,
  Plus,
  SlidersHorizontal,
  Trash2,
  Type,
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
  Toggle,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData, putData } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import type { Language } from '@/types/api';

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

type DataType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'SELECT' | 'MULTI_SELECT';
type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface AttributeOption {
  id: number;
  value: string;
  label: string;
  colorHex: string | null;
  sortOrder: number;
  usageCount: number | null;
}

interface Attribute {
  id: number;
  code: string;
  name: string;
  description: string | null;
  dataType: DataType;
  unit: string | null;
  filterable: boolean;
  showInListing: boolean;
  required: boolean;
  status: Status;
  sortOrder: number;
  groupId: number | null;
  groupName: string | null;
  options: AttributeOption[];
  translations: Record<string, { name: string; description: string | null }>;
}

interface AttributeGroup {
  id: number;
  code: string;
  name: string;
  sortOrder: number;
}

interface AttributeForm {
  id: number | null;
  code: string;
  dataType: DataType;
  unit: string;
  groupId: number | null;
  filterable: boolean;
  showInListing: boolean;
  required: boolean;
  status: Status;
  sortOrder: number;
  translations: Record<string, { name: string; description: string }>;
}

const EMPTY_FORM: AttributeForm = {
  id: null,
  code: '',
  dataType: 'TEXT',
  unit: '',
  groupId: null,
  filterable: false,
  showInListing: false,
  required: false,
  status: 'ACTIVE',
  sortOrder: 0,
  translations: { az: { name: '', description: '' } },
};

const DATA_TYPES: {
  value: DataType;
  label: string;
  hint: string;
  icon: typeof Type;
}[] = [
  { value: 'TEXT', label: 'Mətn', hint: 'Sərbəst yazı — "Made in Italy"', icon: Type },
  { value: 'NUMBER', label: 'Rəqəm', hint: 'Aralıq filtri mümkündür — çəki, en', icon: Hash },
  { value: 'BOOLEAN', label: 'Bəli / Xeyr', hint: 'Yığılma tələb olunur?', icon: Check },
  { value: 'SELECT', label: 'Seçim', hint: 'Hazır siyahıdan biri — rəng, material', icon: List },
  {
    value: 'MULTI_SELECT',
    label: 'Çoxlu seçim',
    hint: 'Siyahıdan bir neçəsi — uyğun otaqlar',
    icon: ListChecks,
  },
];

const DATA_TYPE_LABEL: Record<DataType, string> = Object.fromEntries(
  DATA_TYPES.map((entry) => [entry.value, entry.label]),
) as Record<DataType, string>;

const STATUS_TONE: Record<Status, BadgeTone> = {
  ACTIVE: 'success',
  DRAFT: 'warning',
  ARCHIVED: 'neutral',
};

/** Slugifies a typed name into a machine code, matching the server's `[a-z0-9_]` rule. */
function toCode(name: string): string {
  const map: Record<string, string> = {
    ə: 'e', ı: 'i', ğ: 'g', ş: 's', ç: 'c', ö: 'o', ü: 'u', İ: 'i',
  };
  return name
    .toLowerCase()
    .split('')
    .map((char) => map[char] ?? char)
    .join('')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 64);
}

/**
 * Attribute management.
 *
 * Attributes are the vocabulary the catalog is described in, so this screen is arranged
 * around the one decision that cannot be undone later: the data type. It is picked as a
 * card with an explanation rather than a dropdown, and locked once any product uses the
 * attribute — changing it would orphan every value already stored in the old column.
 */
export function AttributesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [form, setForm] = useState<AttributeForm | null>(null);
  const [activeLanguage, setActiveLanguage] = useState('az');
  const [groupFormOpen, setGroupFormOpen] = useState(false);

  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });

  const attributes = useQuery({
    queryKey: ['admin', 'attributes', 'all'],
    queryFn: () => getData<Attribute[]>('/admin/attributes'),
  });

  const groups = useQuery({
    queryKey: ['admin', 'attributes', 'groups'],
    queryFn: () => getData<AttributeGroup[]>('/admin/attributes/groups'),
  });

  // Loaded separately because only the detail endpoint carries option usage counts,
  // and those decide whether an option may be deleted.
  const selected = useQuery({
    queryKey: ['admin', 'attribute', form?.id],
    queryFn: () => getData<Attribute>(`/admin/attributes/${form!.id}`),
    enabled: Boolean(form?.id),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'attributes'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'attribute'] });
  };

  const save = useMutation({
    mutationFn: (values: AttributeForm) => {
      const payload = {
        code: values.code,
        dataType: values.dataType,
        unit: values.unit || null,
        groupId: values.groupId,
        filterable: values.filterable,
        showInListing: values.showInListing,
        required: values.required,
        status: values.status,
        sortOrder: values.sortOrder,
        translations: Object.fromEntries(
          Object.entries(values.translations)
            .filter(([, translation]) => translation.name.trim())
            .map(([code, translation]) => [
              code,
              { name: translation.name, description: translation.description || null },
            ]),
        ),
      };
      return values.id
        ? putData<Attribute>(`/admin/attributes/${values.id}`, payload)
        : postData<Attribute>('/admin/attributes', payload);
    },
    onSuccess: (saved) => {
      invalidate();
      toast.success('Attribut yadda saxlanıldı');
      // Stay in the editor after creating: the next step is almost always adding options.
      setForm((current) => (current ? { ...current, id: saved.id } : current));
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/attributes/${id}`),
    onSuccess: () => {
      invalidate();
      setForm(null);
      toast.success('Attribut silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const addOption = useMutation({
    mutationFn: (values: { value: string; colorHex: string | null; labels: Record<string, string> }) =>
      postData<Attribute>(`/admin/attributes/${form!.id}/options`, values),
    onSuccess: () => {
      invalidate();
      toast.success('Seçim əlavə edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const deleteOption = useMutation({
    mutationFn: (optionId: number) =>
      deleteRequest(`/admin/attributes/${form!.id}/options/${optionId}`),
    onSuccess: () => {
      invalidate();
      toast.success('Seçim silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const createGroup = useMutation({
    mutationFn: (values: { code: string; names: Record<string, string> }) =>
      postData<AttributeGroup>('/admin/attributes/groups', values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'attributes', 'groups'] });
      setGroupFormOpen(false);
      toast.success('Qrup yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const openEditor = (attribute: Attribute) => {
    const translations: AttributeForm['translations'] = {};
    Object.entries(attribute.translations).forEach(([code, translation]) => {
      translations[code] = {
        name: translation.name,
        description: translation.description ?? '',
      };
    });
    if (!translations['az']) {
      translations['az'] = { name: attribute.name, description: '' };
    }

    setForm({
      id: attribute.id,
      code: attribute.code,
      dataType: attribute.dataType,
      unit: attribute.unit ?? '',
      groupId: attribute.groupId,
      filterable: attribute.filterable,
      showInListing: attribute.showInListing,
      required: attribute.required,
      status: attribute.status,
      sortOrder: attribute.sortOrder,
      translations,
    });
  };

  // Grouped for the list, so related specifications sit together the way they do on
  // the product page.
  const grouped = useMemo(() => {
    const buckets = new Map<string, { name: string; items: Attribute[] }>();
    (attributes.data ?? []).forEach((attribute) => {
      const key = attribute.groupName ?? 'Qrupsuz';
      if (!buckets.has(key)) {
        buckets.set(key, { name: key, items: [] });
      }
      buckets.get(key)!.items.push(attribute);
    });
    return [...buckets.values()];
  }, [attributes.data]);

  const inUse = (selected.data?.options ?? []).some((option) => (option.usageCount ?? 0) > 0);
  const usesOptions = form?.dataType === 'SELECT' || form?.dataType === 'MULTI_SELECT';
  const activeLanguages = languages.data ?? [{ code: 'az' } as Language];

  return (
    <>
      <PageHeader
        title="Attributlar"
        description="Məhsulların texniki xüsusiyyətləri. Filtrlənə bilən attributlar kataloq panelində facet kimi görünür."
        actions={
          <>
            <Button variant="secondary" onClick={() => setGroupFormOpen((open) => !open)}>
              Qrup əlavə et
            </Button>
            <Button
              icon={<Plus size={15} aria-hidden="true" />}
              onClick={() => {
                setForm({ ...EMPTY_FORM, translations: { az: { name: '', description: '' } } });
                setActiveLanguage('az');
              }}
            >
              Yeni attribut
            </Button>
          </>
        }
      />

      {groupFormOpen && (
        <GroupForm
          languages={activeLanguages}
          pending={createGroup.isPending}
          onCancel={() => setGroupFormOpen(false)}
          onSubmit={(values) => createGroup.mutate(values)}
        />
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_400px]">
        {/* ---------------- list ---------------- */}
        <Card bodyClassName="p-0">
          {attributes.isLoading ? (
            <InlineLoader />
          ) : grouped.length === 0 ? (
            <EmptyState
              icon={<SlidersHorizontal size={36} aria-hidden="true" />}
              title="Attribut yoxdur"
              description="Məhsulların xüsusiyyətlərini təsvir etmək üçün ilk attributu yaradın — material, rəng, zəmanət."
              action={
                <Button
                  icon={<Plus size={15} aria-hidden="true" />}
                  onClick={() => setForm({ ...EMPTY_FORM })}
                >
                  Yeni attribut
                </Button>
              }
            />
          ) : (
            <div className="divide-y divide-[var(--color-border)]">
              {grouped.map((group) => (
                <div key={group.name}>
                  <p className="bg-[var(--color-muted)] px-5 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                    {group.name}
                  </p>
                  <ul className="divide-y divide-[var(--color-border)]">
                    {group.items.map((attribute) => {
                      const Icon =
                        DATA_TYPES.find((entry) => entry.value === attribute.dataType)?.icon ?? Braces;
                      return (
                        <li key={attribute.id}>
                          <button
                            type="button"
                            onClick={() => openEditor(attribute)}
                            className={clsx(
                              'flex w-full items-center gap-3 px-5 py-3 text-start transition-colors',
                              form?.id === attribute.id
                                ? 'bg-[var(--color-primary)]/6'
                                : 'hover:bg-[var(--color-muted)]',
                            )}
                          >
                            <GripVertical
                              size={14}
                              className="shrink-0 text-[var(--color-border)]"
                              aria-hidden="true"
                            />
                            <Icon
                              size={16}
                              className="shrink-0 text-[var(--color-subtle)]"
                              aria-hidden="true"
                            />

                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-sm font-medium text-[var(--color-heading)]">
                                {attribute.name}
                                {attribute.unit && (
                                  <span className="ms-1 font-normal text-[var(--color-subtle)]">
                                    ({attribute.unit})
                                  </span>
                                )}
                              </span>
                              <span className="block font-mono text-xs text-[var(--color-subtle)]">
                                {attribute.code}
                              </span>
                            </span>

                            <span className="hidden text-xs text-[var(--color-body)] sm:block">
                              {DATA_TYPE_LABEL[attribute.dataType]}
                              {attribute.options.length > 0 && (
                                <span className="text-[var(--color-subtle)]">
                                  {' · '}
                                  {attribute.options.length} seçim
                                </span>
                              )}
                            </span>

                            {attribute.filterable && <Badge tone="info">Filtr</Badge>}
                            {attribute.status !== 'ACTIVE' && (
                              <Badge tone={STATUS_TONE[attribute.status]}>
                                {attribute.status === 'DRAFT' ? 'Qaralama' : 'Arxiv'}
                              </Badge>
                            )}
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* ---------------- editor ---------------- */}
        <div className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          {!form ? (
            <Card>
              <EmptyState
                icon={<SlidersHorizontal size={30} aria-hidden="true" />}
                title="Attribut seçin"
                description="Redaktə etmək üçün siyahıdan birinə klikləyin və ya yenisini yaradın."
              />
            </Card>
          ) : (
            <>
              <Card
                title={form.id ? 'Attributu redaktə et' : 'Yeni attribut'}
                actions={
                  <Button variant="ghost" size="sm" onClick={() => setForm(null)}>
                    Bağla
                  </Button>
                }
              >
                <div className="space-y-4">
                  {/* --- name (per language) --- */}
                  <div>
                    <div className="mb-1.5 flex items-center justify-between">
                      <span className="text-sm font-medium text-[var(--color-heading)]">
                        Ad
                        <span className="ms-0.5 text-[var(--color-danger)]">*</span>
                      </span>
                      <div className="flex gap-1">
                        {activeLanguages.map((language) => (
                          <button
                            key={language.code}
                            type="button"
                            onClick={() => setActiveLanguage(language.code)}
                            className={clsx(
                              'rounded px-2 py-0.5 text-xs font-semibold uppercase transition-colors',
                              activeLanguage === language.code
                                ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                                : 'text-[var(--color-subtle)] hover:bg-[var(--color-muted)]',
                            )}
                          >
                            {language.code}
                          </button>
                        ))}
                      </div>
                    </div>
                    <Input
                      value={form.translations[activeLanguage]?.name ?? ''}
                      onChange={(event) => {
                        const name = event.target.value;
                        setForm({
                          ...form,
                          translations: {
                            ...form.translations,
                            [activeLanguage]: {
                              name,
                              description: form.translations[activeLanguage]?.description ?? '',
                            },
                          },
                          // Code is derived from the default-language name while creating,
                          // then frozen so an edit cannot break existing references.
                          code:
                            !form.id && activeLanguage === 'az' ? toCode(name) : form.code,
                        });
                      }}
                      placeholder="Material"
                    />
                  </div>

                  <Field
                    label="Kod"
                    htmlFor="attrCode"
                    required
                    hint={
                      form.id
                        ? 'Kodu dəyişmək mövcud inteqrasiyaları poza bilər'
                        : 'Addan avtomatik yaradılır'
                    }
                  >
                    <Input
                      id="attrCode"
                      value={form.code}
                      onChange={(event) =>
                        setForm({ ...form, code: toCode(event.target.value) })
                      }
                      className="font-mono"
                      placeholder="material"
                    />
                  </Field>

                  {/* --- data type --- */}
                  <fieldset>
                    <legend className="mb-1.5 flex items-center gap-2 text-sm font-medium text-[var(--color-heading)]">
                      Data tipi
                      {inUse && (
                        <span
                          className="inline-flex items-center gap-1 text-xs font-normal text-[var(--color-subtle)]"
                          title="Attribut məhsullarda istifadə olunur"
                        >
                          <Lock size={12} aria-hidden="true" />
                          kilidli
                        </span>
                      )}
                    </legend>

                    <div className="grid gap-2">
                      {DATA_TYPES.map((entry) => {
                        const Icon = entry.icon;
                        const isSelected = form.dataType === entry.value;
                        return (
                          <label
                            key={entry.value}
                            className={clsx(
                              'flex items-start gap-2.5 rounded-[var(--radius-button)] border p-2.5 transition-colors',
                              inUse && !isSelected
                                ? 'cursor-not-allowed opacity-40'
                                : 'cursor-pointer',
                              isSelected
                                ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/6'
                                : 'border-[var(--color-border)] hover:bg-[var(--color-muted)]',
                            )}
                          >
                            <input
                              type="radio"
                              name="dataType"
                              value={entry.value}
                              checked={isSelected}
                              disabled={inUse && !isSelected}
                              onChange={() => setForm({ ...form, dataType: entry.value })}
                              className="sr-only"
                            />
                            <Icon
                              size={16}
                              className={clsx(
                                'mt-0.5 shrink-0',
                                isSelected ? 'text-[var(--color-primary)]' : 'text-[var(--color-subtle)]',
                              )}
                              aria-hidden="true"
                            />
                            <span className="min-w-0">
                              <span className="block text-sm font-medium text-[var(--color-heading)]">
                                {entry.label}
                              </span>
                              <span className="block text-xs text-[var(--color-subtle)]">
                                {entry.hint}
                              </span>
                            </span>
                          </label>
                        );
                      })}
                    </div>

                    {inUse && (
                      <p className="mt-2 rounded-[var(--radius-button)] bg-[var(--color-muted)] px-3 py-2 text-xs text-[var(--color-body)]">
                        Bu attribut məhsullarda istifadə olunur. Tipi dəyişmək saxlanmış
                        dəyərləri etibarsız edərdi, ona görə kilidlənib.
                      </p>
                    )}
                  </fieldset>

                  {form.dataType === 'NUMBER' && (
                    <Field
                      label="Ölçü vahidi"
                      htmlFor="attrUnit"
                      hint="Dəyərin yanında göstərilir — sm, kq, ay"
                    >
                      <Input
                        id="attrUnit"
                        value={form.unit}
                        onChange={(event) => setForm({ ...form, unit: event.target.value })}
                        placeholder="sm"
                      />
                    </Field>
                  )}

                  <Field label="Qrup" htmlFor="attrGroup" hint="Xüsusiyyətlər cədvəlində bölmə başlığı">
                    <Select
                      id="attrGroup"
                      value={form.groupId ?? ''}
                      onChange={(event) =>
                        setForm({
                          ...form,
                          groupId: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                    >
                      <option value="">Qrupsuz</option>
                      {(groups.data ?? []).map((group) => (
                        <option key={group.id} value={group.id}>
                          {group.name}
                        </option>
                      ))}
                    </Select>
                  </Field>

                  <div className="space-y-3 border-t border-[var(--color-border)] pt-4">
                    <Toggle
                      label="Filtrlənə bilər"
                      hint="Kataloq səhifəsində facet kimi görünür"
                      checked={form.filterable}
                      onChange={(checked) => setForm({ ...form, filterable: checked })}
                    />
                    <Toggle
                      label="Kartda göstər"
                      hint="Məhsul kartında qısa məlumat kimi"
                      checked={form.showInListing}
                      onChange={(checked) => setForm({ ...form, showInListing: checked })}
                    />
                    <Toggle
                      label="Mütləqdir"
                      hint="Məhsul redaktorunda doldurulması tələb olunur"
                      checked={form.required}
                      onChange={(checked) => setForm({ ...form, required: checked })}
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <Field label="Status" htmlFor="attrStatus">
                      <Select
                        id="attrStatus"
                        value={form.status}
                        onChange={(event) =>
                          setForm({ ...form, status: event.target.value as Status })
                        }
                      >
                        <option value="ACTIVE">Aktiv</option>
                        <option value="DRAFT">Qaralama</option>
                        <option value="ARCHIVED">Arxiv</option>
                      </Select>
                    </Field>

                    <Field label="Sıra" htmlFor="attrSort">
                      <Input
                        id="attrSort"
                        type="number"
                        value={form.sortOrder}
                        onChange={(event) =>
                          setForm({ ...form, sortOrder: Number(event.target.value) })
                        }
                      />
                    </Field>
                  </div>

                  <div className="flex gap-2">
                    <Button
                      className="flex-1"
                      loading={save.isPending}
                      disabled={!form.code || !form.translations['az']?.name?.trim()}
                      onClick={() => save.mutate(form)}
                    >
                      Yadda saxla
                    </Button>
                    {form.id && (
                      <Button
                        variant="ghost"
                        className="text-[var(--color-danger)]"
                        title="Sil"
                        onClick={() => {
                          if (window.confirm('Attribut silinsin?')) {
                            remove.mutate(form.id!);
                          }
                        }}
                        icon={<Trash2 size={15} aria-hidden="true" />}
                      />
                    )}
                  </div>
                </div>
              </Card>

              {/* --- options --- */}
              {usesOptions && (
                <OptionsCard
                  saved={Boolean(form.id)}
                  options={selected.data?.options ?? []}
                  languages={activeLanguages}
                  pending={addOption.isPending}
                  onAdd={(values) => addOption.mutate(values)}
                  onDelete={(optionId) => deleteOption.mutate(optionId)}
                />
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}

/**
 * Option list for SELECT / MULTI_SELECT attributes.
 *
 * An option that products already use is shown with its usage count and no delete button:
 * removing it would silently blank the specification on every product that selected it.
 */
function OptionsCard({
  saved,
  options,
  languages,
  pending,
  onAdd,
  onDelete,
}: {
  saved: boolean;
  options: AttributeOption[];
  languages: Language[];
  pending: boolean;
  onAdd: (values: { value: string; colorHex: string | null; labels: Record<string, string> }) => void;
  onDelete: (optionId: number) => void;
}) {
  const [label, setLabel] = useState('');
  const [colorHex, setColorHex] = useState('');
  const [withColor, setWithColor] = useState(false);

  if (!saved) {
    return (
      <Card title="Seçimlər">
        <p className="text-sm text-[var(--color-subtle)]">
          Seçim əlavə etmək üçün əvvəlcə attributu yadda saxlayın.
        </p>
      </Card>
    );
  }

  const submit = () => {
    const trimmed = label.trim();
    if (!trimmed) return;

    // The machine value is derived once from the default-language label; other
    // languages only supply display text.
    onAdd({
      value: toCode(trimmed),
      colorHex: withColor && colorHex ? colorHex : null,
      labels: Object.fromEntries(languages.map((language) => [language.code, trimmed])),
    });
    setLabel('');
    setColorHex('');
  };

  return (
    <Card title="Seçimlər" description={`${formatNumber(options.length)} seçim`}>
      {options.length > 0 && (
        <ul className="mb-4 divide-y divide-[var(--color-border)]">
          {options.map((option) => {
            const used = (option.usageCount ?? 0) > 0;
            return (
              <li key={option.id} className="flex items-center gap-2.5 py-2">
                {option.colorHex && (
                  <span
                    className="h-4 w-4 shrink-0 rounded-full border border-black/10"
                    style={{ backgroundColor: option.colorHex }}
                    aria-hidden="true"
                  />
                )}
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm text-[var(--color-heading)]">
                    {option.label}
                  </span>
                  <span className="block font-mono text-xs text-[var(--color-subtle)]">
                    {option.value}
                  </span>
                </span>

                {used ? (
                  <span
                    className="flex items-center gap-1 text-xs text-[var(--color-subtle)]"
                    title="İstifadədə olan seçim silinə bilməz"
                  >
                    <Lock size={11} aria-hidden="true" />
                    {formatNumber(option.usageCount!)} məhsul
                  </span>
                ) : (
                  <Button
                    variant="ghost"
                    size="sm"
                    title="Sil"
                    className="text-[var(--color-danger)]"
                    onClick={() => onDelete(option.id)}
                    icon={<Trash2 size={14} aria-hidden="true" />}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}

      <div className="space-y-3 border-t border-[var(--color-border)] pt-4">
        <Field label="Yeni seçim" htmlFor="optionLabel">
          <div className="flex gap-2">
            {withColor && (
              <input
                type="color"
                value={colorHex || '#000000'}
                onChange={(event) => setColorHex(event.target.value)}
                aria-label="Seçim rəngi"
                className="h-10 w-12 shrink-0 cursor-pointer rounded-[var(--radius-input)] border border-[var(--color-border)]"
              />
            )}
            <Input
              id="optionLabel"
              value={label}
              onChange={(event) => setLabel(event.target.value)}
              onKeyDown={(event) => {
                // Enter submits: adding ten colours should not need ten mouse trips.
                if (event.key === 'Enter') {
                  event.preventDefault();
                  submit();
                }
              }}
              placeholder="Ağac"
            />
            <Button size="sm" loading={pending} disabled={!label.trim()} onClick={submit}>
              Əlavə et
            </Button>
          </div>
        </Field>

        <Toggle
          label="Rəng nümunəsi"
          hint="Rəng kimi attributlar üçün — filtrdə dairə göstərilir"
          checked={withColor}
          onChange={setWithColor}
        />
      </div>
    </Card>
  );
}

function GroupForm({
  languages,
  pending,
  onCancel,
  onSubmit,
}: {
  languages: Language[];
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: { code: string; names: Record<string, string> }) => void;
}) {
  const [name, setName] = useState('');

  useEffect(() => setName(''), []);

  return (
    <Card
      title="Yeni attribut qrupu"
      description="Xüsusiyyətlər cədvəlində bölmə başlığı — “Ölçülər”, “Material”"
      className="mb-6"
      actions={
        <Button variant="ghost" size="sm" onClick={onCancel}>
          Bağla
        </Button>
      }
    >
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Qrup adı" htmlFor="groupName" className="min-w-[220px] flex-1">
          <Input
            id="groupName"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Ölçülər"
          />
        </Field>
        <Button
          loading={pending}
          disabled={!name.trim()}
          onClick={() =>
            onSubmit({
              code: toCode(name),
              names: Object.fromEntries(languages.map((language) => [language.code, name.trim()])),
            })
          }
        >
          Yarat
        </Button>
      </div>
    </Card>
  );
}
