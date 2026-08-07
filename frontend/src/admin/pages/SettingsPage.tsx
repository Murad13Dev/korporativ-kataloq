import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Building2, History, Palette, Rocket, ToggleRight, Undo2 } from 'lucide-react';
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
import { apiClient, getData, postData, putData } from '@/lib/api';
import { formatDateTime } from '@/lib/format';
import { siteConfigQueryKey } from '@/hooks/useSiteConfig';
import type { SiteConfiguration } from '@/types/api';

interface VersionResponse {
  id: number;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  label: string | null;
  configuration: SiteConfiguration;
  createdAt: string;
  publishedAt: string | null;
}

type Tab = 'company' | 'theme' | 'features' | 'versions';

const TABS: { id: Tab; label: string; icon: typeof Building2 }[] = [
  { id: 'company', label: 'Şirkət', icon: Building2 },
  { id: 'theme', label: 'Dizayn', icon: Palette },
  { id: 'features', label: 'Funksiyalar', icon: ToggleRight },
  { id: 'versions', label: 'Versiyalar', icon: History },
];

/** Theme colours grouped the way an operator thinks about them, not the way they are stored. */
const COLOR_GROUPS: { title: string; fields: { key: string; label: string; hint?: string }[] }[] = [
  {
    title: 'Brend',
    fields: [
      { key: 'primaryColor', label: 'Əsas rəng', hint: 'Düymələr, linklər, vurğular' },
      { key: 'primaryHoverColor', label: 'Əsas rəng (hover)' },
      { key: 'accentColor', label: 'Aksent' },
      { key: 'secondaryColor', label: 'İkinci dərəcəli' },
    ],
  },
  {
    title: 'Endirim',
    fields: [
      { key: 'saleColor', label: 'Endirim rəngi', hint: 'Lentlər və endirimli qiymətlər' },
    ],
  },
  {
    title: 'Səth və mətn',
    fields: [
      { key: 'backgroundColor', label: 'Fon' },
      { key: 'sectionBackgroundColor', label: 'Bölmə fonu' },
      { key: 'borderColor', label: 'Sərhəd' },
      { key: 'headingColor', label: 'Başlıq mətni' },
      { key: 'textColor', label: 'Əsas mətn' },
      { key: 'mutedTextColor', label: 'Solğun mətn' },
    ],
  },
  {
    title: 'Alt hissə',
    fields: [
      { key: 'footerBackgroundColor', label: 'Footer fonu' },
      { key: 'footerTextColor', label: 'Footer mətni' },
    ],
  },
];

/**
 * Ready-made colour schemes. Picking one bulk-replaces every field in `COLOR_GROUPS` plus
 * typography/radius, but through the same `update(['theme'], …)` path a single manual
 * field edit uses — so a preset is just a fast starting point, never a locked mode. An
 * operator can still hand-tune any field afterwards without "losing" the preset except
 * for its name tag.
 */
const THEME_PRESETS: { id: string; label: string; description: string; tokens: Record<string, unknown> }[] = [
  {
    id: 'INK_ELECTRIC',
    label: 'Ink & Electric',
    description: 'Standart — tünd indiqo və elektrik firuzəyi',
    tokens: {
      primaryColor: '#4F46E5', primaryHoverColor: '#4338CA', primaryContrastColor: '#FFFFFF',
      secondaryColor: '#0F172A', accentColor: '#06B6D4', saleColor: '#F43F5E',
      successColor: '#10B981', warningColor: '#F59E0B', dangerColor: '#EF4444',
      backgroundColor: '#FFFFFF', sectionBackgroundColor: '#F8FAFC',
      headingColor: '#0F172A', textColor: '#475569', mutedTextColor: '#94A3B8', borderColor: '#E2E8F0',
      footerBackgroundColor: '#0B1120', footerTextColor: '#94A3B8',
      fontFamily: 'Inter', headingFontFamily: 'Sora',
      buttonRadius: 10, cardRadius: 16, inputRadius: 10,
    },
  },
  {
    id: 'SABAH',
    label: 'Sabah',
    description: 'İsti səhər tonları — narıncı və krem',
    tokens: {
      primaryColor: '#EA580C', primaryHoverColor: '#C2410C', primaryContrastColor: '#FFFFFF',
      secondaryColor: '#78350F', accentColor: '#F59E0B', saleColor: '#DC2626',
      successColor: '#16A34A', warningColor: '#D97706', dangerColor: '#DC2626',
      backgroundColor: '#FFFBF5', sectionBackgroundColor: '#FEF3E7',
      headingColor: '#431407', textColor: '#78716C', mutedTextColor: '#A8A29E', borderColor: '#F0E4D4',
      footerBackgroundColor: '#3B2412', footerTextColor: '#D6C4AE',
      fontFamily: 'Inter', headingFontFamily: 'Inter',
      buttonRadius: 14, cardRadius: 20, inputRadius: 12,
    },
  },
  {
    id: 'MERMER',
    label: 'Mərmər',
    description: 'Zərif neytral — daş və tunc',
    tokens: {
      primaryColor: '#27272A', primaryHoverColor: '#18181B', primaryContrastColor: '#FFFFFF',
      secondaryColor: '#57534E', accentColor: '#B08D57', saleColor: '#9F1239',
      successColor: '#15803D', warningColor: '#B45309', dangerColor: '#9F1239',
      backgroundColor: '#FAF9F7', sectionBackgroundColor: '#F3F1EC',
      headingColor: '#1C1917', textColor: '#57534E', mutedTextColor: '#A8A29E', borderColor: '#E7E2D9',
      footerBackgroundColor: '#1C1917', footerTextColor: '#A8A29E',
      fontFamily: 'system-ui', headingFontFamily: 'Sora',
      buttonRadius: 4, cardRadius: 8, inputRadius: 4,
    },
  },
  {
    id: 'NEON_BAZAR',
    label: 'Neon Bazar',
    description: 'Enerjili bazar — bənövşəyi və neon firuzəyi',
    tokens: {
      primaryColor: '#7C3AED', primaryHoverColor: '#6D28D9', primaryContrastColor: '#FFFFFF',
      secondaryColor: '#1E1B4B', accentColor: '#22D3EE', saleColor: '#F43F5E',
      successColor: '#22C55E', warningColor: '#FACC15', dangerColor: '#F43F5E',
      backgroundColor: '#FFFFFF', sectionBackgroundColor: '#F5F3FF',
      headingColor: '#1E1B4B', textColor: '#44403C', mutedTextColor: '#8B85A0', borderColor: '#E9D5FF',
      footerBackgroundColor: '#1E1B4B', footerTextColor: '#C4B5FD',
      fontFamily: 'Inter', headingFontFamily: 'Sora',
      buttonRadius: 24, cardRadius: 20, inputRadius: 14,
    },
  },
];

const FEATURE_FIELDS: { key: string; label: string; hint: string }[] = [
  { key: 'commerceEnabled', label: 'Onlayn satış', hint: 'Söndürülsə sayt yalnız kataloq kimi işləyir' },
  { key: 'cartEnabled', label: 'Səbət', hint: 'Səbətə əlavə etmə düymələri' },
  { key: 'onlinePaymentEnabled', label: 'Onlayn ödəniş', hint: 'Kart ilə ödəniş seçimi' },
  { key: 'guestCheckoutEnabled', label: 'Qonaq sifarişi', hint: 'Qeydiyyatsız sifariş vermək' },
  { key: 'showPricesToGuests', label: 'Qiymətləri hamıya göstər', hint: 'Söndürülsə yalnız qeydiyyatlı istifadəçilər görür' },
  { key: 'showStockStatus', label: 'Anbar vəziyyəti', hint: '"Stokda var" / "Son ədədlər"' },
  { key: 'wishlistEnabled', label: 'Seçilmişlər', hint: 'Məhsulu yadda saxlamaq' },
  { key: 'reviewsEnabled', label: 'Rəylər', hint: 'Məhsul reytinqi və rəylər (admin təsdiqi ilə)' },
  { key: 'chatEnabled', label: 'Canlı dəstək', hint: 'Sayt üzərində chat pəncərəsi' },
  { key: 'blogEnabled', label: 'Bloq', hint: 'Bloq bölməsi və menyu linki' },
  { key: 'showNewsletter', label: 'Xəbər bülleteni', hint: 'Abunə forması' },
  { key: 'showSearch', label: 'Axtarış', hint: 'Header-də axtarış düyməsi' },
  { key: 'showLanguageSelector', label: 'Dil seçimi', hint: 'Dillər arasında keçid' },
];

/**
 * Site settings.
 *
 * Everything edits a *draft*. The live site only changes when the operator presses
 * "Dərc et", and the previous version is archived rather than overwritten — so a bad
 * publish is one click away from being undone.
 */
export function SettingsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [tab, setTab] = useState<Tab>('company');
  const [draft, setDraft] = useState<SiteConfiguration | null>(null);

  const published = useQuery({
    queryKey: ['admin', 'site-config', 'published'],
    queryFn: () => getData<VersionResponse>('/admin/site-configuration/published'),
  });

  const draftQuery = useQuery({
    queryKey: ['admin', 'site-config', 'draft'],
    queryFn: async () => {
      try {
        return await getData<VersionResponse>('/admin/site-configuration/draft');
      } catch {
        // No open draft is the normal state right after a publish.
        return null;
      }
    },
  });

  const versions = useQuery({
    queryKey: ['admin', 'site-config', 'versions'],
    queryFn: () => apiClient.get('/admin/site-configuration/versions').then((r) => r.data),
    enabled: tab === 'versions',
  });

  // Seed the editor from the draft if one exists, otherwise from what is live.
  useEffect(() => {
    if (draft) return;
    const source = draftQuery.data?.configuration ?? published.data?.configuration;
    if (source) {
      setDraft(structuredClone(source));
    }
  }, [draft, draftQuery.data, published.data]);

  const hasDraft = Boolean(draftQuery.data);

  const save = useMutation({
    mutationFn: (configuration: SiteConfiguration) =>
      putData('/admin/site-configuration/draft', { configuration }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'site-config'] });
      toast.success('Qaralama yadda saxlanıldı');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const publish = useMutation({
    mutationFn: () => postData('/admin/site-configuration/publish'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'site-config'] });
      // The storefront reads the same document; drop its cache so the theme repaints.
      queryClient.invalidateQueries({ queryKey: siteConfigQueryKey });
      toast.success('Dəyişikliklər saytda dərc edildi');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const restore = useMutation({
    mutationFn: (versionId: number) =>
      postData(`/admin/site-configuration/versions/${versionId}/restore`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'site-config'] });
      setDraft(null);
      toast.success('Versiya qaralama olaraq bərpa edildi. Dərc etməyi unutmayın.');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const update = (path: string[], value: unknown) => {
    setDraft((current) => {
      if (!current) return current;
      const next = structuredClone(current) as Record<string, any>;
      let cursor = next;
      for (const segment of path.slice(0, -1)) {
        cursor[segment] ??= {};
        cursor = cursor[segment];
      }
      cursor[path[path.length - 1]!] = value;
      return next as SiteConfiguration;
    });
  };

  if (published.isLoading || !draft) {
    return <InlineLoader label="Parametrlər yüklənir…" />;
  }

  return (
    <>
      <PageHeader
        title="Sayt parametrləri"
        description="Dəyişikliklər qaralamada saxlanılır və yalnız dərc edildikdən sonra saytda görünür."
        actions={
          <>
            {hasDraft && <Badge tone="warning">Dərc edilməmiş dəyişikliklər</Badge>}
            <Button variant="secondary" loading={save.isPending} onClick={() => save.mutate(draft)}>
              Qaralamanı saxla
            </Button>
            <Button
              loading={publish.isPending}
              disabled={!hasDraft}
              icon={<Rocket size={15} aria-hidden="true" />}
              onClick={() => {
                if (window.confirm('Dəyişikliklər saytda dərhal görünəcək. Davam edilsin?')) {
                  publish.mutate();
                }
              }}
            >
              Dərc et
            </Button>
          </>
        }
      />

      {/* ---------------- tabs ---------------- */}
      <div className="mb-6 flex flex-wrap gap-1 border-b border-[var(--color-border)]">
        {TABS.map((entry) => {
          const Icon = entry.icon;
          return (
            <button
              key={entry.id}
              type="button"
              onClick={() => setTab(entry.id)}
              aria-current={tab === entry.id ? 'page' : undefined}
              className={clsx(
                'flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors',
                tab === entry.id
                  ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                  : 'border-transparent text-[var(--color-body)] hover:text-[var(--color-heading)]',
              )}
            >
              <Icon size={16} aria-hidden="true" />
              {entry.label}
            </button>
          );
        })}
      </div>

      {/* ---------------- company ---------------- */}
      {tab === 'company' && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title="Şirkət məlumatları">
            <div className="space-y-4">
              <Field label="Ad" htmlFor="companyName" required>
                <Input
                  id="companyName"
                  value={draft.company?.name ?? ''}
                  onChange={(event) => update(['company', 'name'], event.target.value)}
                />
              </Field>
              <Field label="Şüar" htmlFor="slogan">
                <Input
                  id="slogan"
                  value={draft.company?.slogan ?? ''}
                  onChange={(event) => update(['company', 'slogan'], event.target.value)}
                />
              </Field>
              <Field label="Qısa təsvir" htmlFor="shortDescription">
                <Textarea
                  id="shortDescription"
                  rows={3}
                  value={draft.company?.shortDescription ?? ''}
                  onChange={(event) => update(['company', 'shortDescription'], event.target.value)}
                />
              </Field>
              <Field label="Ünvan" htmlFor="address">
                <Input
                  id="address"
                  value={draft.company?.address ?? ''}
                  onChange={(event) => update(['company', 'address'], event.target.value)}
                />
              </Field>
              <Field label="İş saatları" htmlFor="workingHours">
                <Input
                  id="workingHours"
                  value={draft.company?.workingHours ?? ''}
                  onChange={(event) => update(['company', 'workingHours'], event.target.value)}
                />
              </Field>
            </div>
          </Card>

          <Card title="Əlaqə">
            <div className="space-y-4">
              <Field label="Telefon" htmlFor="phone">
                <Input
                  id="phone"
                  value={draft.company?.phones?.[0] ?? ''}
                  onChange={(event) => update(['company', 'phones'], [event.target.value])}
                />
              </Field>
              <Field label="E-poçt" htmlFor="email">
                <Input
                  id="email"
                  type="email"
                  value={draft.company?.email ?? ''}
                  onChange={(event) => update(['company', 'email'], event.target.value)}
                />
              </Field>
              <Field label="Satış e-poçtu" htmlFor="salesEmail">
                <Input
                  id="salesEmail"
                  type="email"
                  value={draft.company?.salesEmail ?? ''}
                  onChange={(event) => update(['company', 'salesEmail'], event.target.value)}
                />
              </Field>
              <Field label="WhatsApp" htmlFor="whatsapp">
                <Input
                  id="whatsapp"
                  value={draft.company?.whatsapp ?? ''}
                  onChange={(event) => update(['company', 'whatsapp'], event.target.value)}
                />
              </Field>
              <Field label="Copyright mətni" htmlFor="copyright">
                <Input
                  id="copyright"
                  value={draft.legal?.copyright ?? ''}
                  onChange={(event) => update(['legal', 'copyright'], event.target.value)}
                />
              </Field>
            </div>
          </Card>
        </div>
      )}

      {/* ---------------- theme ---------------- */}
      {tab === 'theme' && (
        <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
          <div className="space-y-6">
            <Card
              title="Hazır temalar"
              description="Bir kliklə bütün rəng sxemini dəyişin, sonra istənilən sahəni əl ilə tənzimləyin"
            >
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {THEME_PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => update(['theme'], { ...draft.theme, ...preset.tokens, preset: preset.id })}
                    className={clsx(
                      'flex flex-col items-start gap-2.5 rounded-[var(--radius-card)] border p-3 text-start transition-colors',
                      draft.theme?.preset === preset.id
                        ? 'border-[var(--color-primary)] ring-2 ring-[var(--color-primary)]/20'
                        : 'border-[var(--color-border)] hover:border-[var(--color-primary)]/50',
                    )}
                  >
                    <div className="flex gap-1">
                      {(['primaryColor', 'accentColor', 'secondaryColor'] as const).map((key) => (
                        <span
                          key={key}
                          className="h-5 w-5 rounded-full border border-black/10"
                          style={{ background: preset.tokens[key] as string }}
                        />
                      ))}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-[var(--color-heading)]">{preset.label}</p>
                      <p className="text-xs text-[var(--color-subtle)]">{preset.description}</p>
                    </div>
                  </button>
                ))}
              </div>
            </Card>

            {COLOR_GROUPS.map((group) => (
              <Card key={group.title} title={group.title}>
                <div className="grid gap-4 sm:grid-cols-2">
                  {group.fields.map((field) => (
                    <ColorField
                      key={field.key}
                      label={field.label}
                      hint={field.hint}
                      value={(draft.theme as any)?.[field.key] ?? ''}
                      onChange={(value) => update(['theme', field.key], value)}
                    />
                  ))}
                </div>
              </Card>
            ))}

            <Card title="Tipografiya və forma">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Mətn şrifti" htmlFor="fontFamily">
                  <Select
                    id="fontFamily"
                    value={draft.theme?.fontFamily ?? 'Inter'}
                    onChange={(event) => update(['theme', 'fontFamily'], event.target.value)}
                  >
                    <option value="Inter">Inter</option>
                    <option value="Sora">Sora</option>
                    <option value="system-ui">Sistem şrifti</option>
                  </Select>
                </Field>
                <Field label="Başlıq şrifti" htmlFor="headingFontFamily">
                  <Select
                    id="headingFontFamily"
                    value={draft.theme?.headingFontFamily ?? 'Sora'}
                    onChange={(event) => update(['theme', 'headingFontFamily'], event.target.value)}
                  >
                    <option value="Sora">Sora</option>
                    <option value="Inter">Inter</option>
                    <option value="system-ui">Sistem şrifti</option>
                  </Select>
                </Field>

                <SliderField
                  label="Düymə künc radiusu"
                  value={draft.theme?.buttonRadius ?? 10}
                  min={0}
                  max={40}
                  onChange={(value) => update(['theme', 'buttonRadius'], value)}
                />
                <SliderField
                  label="Kart künc radiusu"
                  value={draft.theme?.cardRadius ?? 16}
                  min={0}
                  max={40}
                  onChange={(value) => update(['theme', 'cardRadius'], value)}
                />
                <SliderField
                  label="Əsas şrift ölçüsü"
                  value={draft.theme?.baseFontSize ?? 16}
                  min={12}
                  max={20}
                  onChange={(value) => update(['theme', 'baseFontSize'], value)}
                />
                <SliderField
                  label="Konteyner eni"
                  value={draft.theme?.containerWidth ?? 1280}
                  min={960}
                  max={1920}
                  step={20}
                  onChange={(value) => update(['theme', 'containerWidth'], value)}
                />
              </div>
            </Card>
          </div>

          <div className="lg:sticky lg:top-24 lg:self-start">
            <Card title="Önizləmə" description="Dəyişikliklər dərhal burada görünür">
              <ThemePreview theme={draft.theme} companyName={draft.company?.name ?? 'Şirkət'} />
            </Card>
          </div>
        </div>
      )}

      {/* ---------------- features ---------------- */}
      {tab === 'features' && (
        <Card title="Funksiyalar" description="Saytın hansı bölmələrinin aktiv olacağını seçin">
          <div className="grid gap-5 sm:grid-cols-2">
            {FEATURE_FIELDS.map((feature) => (
              <Toggle
                key={feature.key}
                label={feature.label}
                hint={feature.hint}
                checked={Boolean((draft.features as any)?.[feature.key])}
                onChange={(checked) => update(['features', feature.key], checked)}
              />
            ))}
          </div>
        </Card>
      )}

      {/* ---------------- versions ---------------- */}
      {tab === 'versions' && (
        <Card
          title="Versiya tarixçəsi"
          description="Bərpa köhnə versiyanı birbaşa dərc etmir — ondan yeni qaralama yaradır."
          bodyClassName="p-0"
        >
          {!versions.data ? (
            <InlineLoader />
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {(versions.data.content ?? []).map((version: any) => (
                <li key={version.id} className="flex items-center gap-4 px-5 py-3.5">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-[var(--color-heading)]">
                      Versiya {version.versionNumber}
                      {version.label && (
                        <span className="ms-2 font-normal text-[var(--color-subtle)]">
                          {version.label}
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-[var(--color-subtle)]">
                      {formatDateTime(version.publishedAt ?? version.createdAt)}
                    </p>
                  </div>
                  <Badge
                    tone={
                      version.status === 'PUBLISHED'
                        ? 'success'
                        : version.status === 'DRAFT'
                          ? 'warning'
                          : 'neutral'
                    }
                  >
                    {version.status === 'PUBLISHED'
                      ? 'Canlı'
                      : version.status === 'DRAFT'
                        ? 'Qaralama'
                        : 'Arxiv'}
                  </Badge>
                  {version.status === 'ARCHIVED' && (
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={<Undo2 size={14} aria-hidden="true" />}
                      onClick={() => restore.mutate(version.id)}
                    >
                      Bərpa et
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </>
  );
}

function ColorField({
  label,
  hint,
  value,
  onChange,
}: {
  label: string;
  hint?: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field label={label} hint={hint}>
      <div className="flex gap-2">
        <input
          type="color"
          value={/^#[0-9a-f]{6}$/i.test(value) ? value : '#000000'}
          onChange={(event) => onChange(event.target.value)}
          aria-label={`${label} rəng seçimi`}
          className="h-10 w-14 shrink-0 cursor-pointer rounded-[var(--radius-input)] border border-[var(--color-border)]"
        />
        <Input value={value} onChange={(event) => onChange(event.target.value)} placeholder="#000000" />
      </div>
    </Field>
  );
}

function SliderField({
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (value: number) => void;
}) {
  return (
    <Field label={`${label} — ${value}px`}>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="w-full accent-[var(--color-primary)]"
      />
    </Field>
  );
}

/**
 * Isolated theme preview.
 *
 * Renders inside its own scope with the draft's tokens applied locally, so the operator
 * sees the result without repainting the admin panel they are standing in.
 */
function ThemePreview({
  theme,
  companyName,
}: {
  theme: SiteConfiguration['theme'] | undefined;
  companyName: string;
}) {
  const style = useMemo(() => {
    const safe = (value: string | undefined, fallback: string) =>
      value && /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(value) ? value : fallback;

    return {
      '--preview-primary': safe(theme?.primaryColor, '#4F46E5'),
      '--preview-sale': safe(theme?.saleColor, '#F43F5E'),
      '--preview-bg': safe(theme?.backgroundColor, '#FFFFFF'),
      '--preview-muted': safe(theme?.sectionBackgroundColor, '#F8FAFC'),
      '--preview-border': safe(theme?.borderColor, '#E2E8F0'),
      '--preview-heading': safe(theme?.headingColor, '#0F172A'),
      '--preview-text': safe(theme?.textColor, '#475569'),
      '--preview-footer': safe(theme?.footerBackgroundColor, '#0B1120'),
      '--preview-card-radius': `${theme?.cardRadius ?? 16}px`,
      '--preview-button-radius': `${theme?.buttonRadius ?? 10}px`,
    } as React.CSSProperties;
  }, [theme]);

  return (
    <div
      style={style}
      className="overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)]"
    >
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ background: 'var(--preview-bg)', borderBottom: '1px solid var(--preview-border)' }}
      >
        <span className="text-sm font-bold" style={{ color: 'var(--preview-heading)' }}>
          {companyName}
        </span>
        <span
          className="px-3 py-1.5 text-xs font-semibold text-white"
          style={{ background: 'var(--preview-primary)', borderRadius: 'var(--preview-button-radius)' }}
        >
          Əlaqə
        </span>
      </div>

      <div className="p-4" style={{ background: 'var(--preview-muted)' }}>
        <div
          className="relative overflow-hidden"
          style={{
            background: 'var(--preview-bg)',
            border: '1px solid var(--preview-border)',
            borderRadius: 'var(--preview-card-radius)',
          }}
        >
          <div className="ribbon" aria-hidden="true">
            <span className="ribbon__label" style={{ '--ribbon-color': 'var(--preview-sale)' } as React.CSSProperties}>
              -25%
            </span>
          </div>

          <div className="flex aspect-[4/3] items-center justify-center text-xs" style={{ background: 'var(--preview-muted)', color: 'var(--preview-text)' }}>
            məhsul şəkli
          </div>

          <div className="p-3">
            <p className="text-sm font-semibold" style={{ color: 'var(--preview-heading)' }}>
              Nümunə məhsul
            </p>
            <div className="mt-1 flex items-baseline gap-2">
              <span className="text-lg font-bold tabular" style={{ color: 'var(--preview-sale)' }}>
                186.75 ₼
              </span>
              <span className="text-sm line-through tabular" style={{ color: 'var(--preview-text)' }}>
                249.00 ₼
              </span>
            </div>
            <div
              className="mt-3 py-2 text-center text-xs font-semibold text-white"
              style={{ background: 'var(--preview-primary)', borderRadius: 'var(--preview-button-radius)' }}
            >
              Səbətə at
            </div>
          </div>
        </div>
      </div>

      <div className="px-4 py-3 text-xs" style={{ background: 'var(--preview-footer)', color: '#94A3B8' }}>
        © {new Date().getFullYear()} {companyName}
      </div>
    </div>
  );
}
