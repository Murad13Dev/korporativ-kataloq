import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  ArrowLeft,
  Copy,
  FileText,
  ImagePlus,
  Info,
  Layers,
  Package,
  Plus,
  Save,
  Star,
  Tag,
  Trash2,
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
import { MediaPicker, type PickedMedia } from '../components/MediaPicker';
import { useToast } from '@/components/ui/Toast';
import {
  ApiError,
  deleteRequest,
  getData,
  patchData,
  postData,
  putData,
} from '@/lib/api';
import { formatFileSize, formatPrice } from '@/lib/format';
import type { CategoryCard, Language, PriceInfo } from '@/types/api';

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

type DataType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'SELECT' | 'MULTI_SELECT';

interface AttributeDefinition {
  id: number;
  code: string;
  name: string;
  dataType: DataType;
  unit: string | null;
  required: boolean;
  groupId: number | null;
  groupName: string | null;
  options: { id: number; label: string; colorHex: string | null }[];
}

interface TranslationDraft {
  name: string;
  shortDescription: string;
  description: string;
  seoTitle: string;
  seoDescription: string;
}

interface AttributeValueDraft {
  attributeId: number;
  optionId: number | null;
  textValue: string | null;
  numberValue: number | null;
  booleanValue: boolean | null;
}

interface ProductImage {
  id: number;
  mediaId: number;
  url: string;
  alt: string | null;
  main: boolean;
  sortOrder: number;
}

interface ProductDocument {
  id: number;
  mediaId: number;
  url: string;
  title: string;
  documentType: string;
  fileSize: number;
}

interface ProductVariantAdmin {
  id: number;
  sku: string;
  priceOverride: number | null;
  trackStock: boolean;
  stockQuantity: number;
  allowBackorder: boolean;
  imageMediaId: number | null;
  imageUrl: string | null;
  sortOrder: number;
  isDefault: boolean;
  active: boolean;
  optionIds: number[];
  optionLabel: string;
}

interface ProductResponse {
  id: number;
  sku: string;
  barcode: string | null;
  slug: string;
  categoryId: number | null;
  price: number | null;
  compareAtPrice: number | null;
  costPrice: number | null;
  currency: string;
  taxRate: number;
  showPrice: boolean;
  purchasable: boolean;
  trackStock: boolean;
  stockQuantity: number;
  lowStockThreshold: number;
  allowBackorder: boolean;
  minOrderQuantity: number;
  maxOrderQuantity: number | null;
  weightGrams: number | null;
  featured: boolean;
  isNew: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  sortOrder: number;
  effectivePrice: PriceInfo;
  translations: Record<string, TranslationDraft>;
  images: ProductImage[];
  documents: ProductDocument[];
  attributes: AttributeValueDraft[];
}

type Tab = 'general' | 'pricing' | 'media' | 'specs' | 'variants' | 'seo';

const TABS: { id: Tab; label: string; icon: typeof Info }[] = [
  { id: 'general', label: 'Ümumi', icon: Info },
  { id: 'pricing', label: 'Qiymət və anbar', icon: Tag },
  { id: 'media', label: 'Şəkillər və sənədlər', icon: ImagePlus },
  { id: 'specs', label: 'Xüsusiyyətlər', icon: Package },
  { id: 'variants', label: 'Variantlar', icon: Layers },
  { id: 'seo', label: 'SEO', icon: FileText },
];

const EMPTY_TRANSLATION: TranslationDraft = {
  name: '',
  shortDescription: '',
  description: '',
  seoTitle: '',
  seoDescription: '',
};

/**
 * Product editor.
 *
 * Two decisions shape this screen:
 *
 * 1. **Everything except media edits a local draft**, saved in one request. A product has
 *    ~20 interdependent fields; autosaving each one would produce invalid intermediate
 *    states server-side (a price without a currency, an ACTIVE product with no name).
 *
 * 2. **Images and documents save immediately.** They have their own endpoints, they are
 *    uploaded one at a time anyway, and losing an upload because the form failed
 *    validation elsewhere is the most annoying possible outcome.
 */
export function ProductEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [tab, setTab] = useState<Tab>('general');
  const [activeLanguage, setActiveLanguage] = useState('az');
  const [draft, setDraft] = useState<ProductResponse | null>(null);
  const [dirty, setDirty] = useState(false);
  const [imagePickerOpen, setImagePickerOpen] = useState(false);
  const [documentPickerOpen, setDocumentPickerOpen] = useState(false);
  const [discountPanelOpen, setDiscountPanelOpen] = useState(false);
  const [editingVariant, setEditingVariant] = useState<ProductVariantAdmin | 'new' | null>(null);

  // --- reference data -----------------------------------------------------

  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });

  const categories = useQuery({
    queryKey: ['admin', 'categories', 'flat'],
    queryFn: () => getData<CategoryCard[]>('/admin/categories'),
    staleTime: 5 * 60 * 1000,
  });

  const attributes = useQuery({
    queryKey: ['admin', 'attributes'],
    queryFn: () => getData<AttributeDefinition[]>('/admin/attributes', { status: 'ACTIVE' }),
    staleTime: 5 * 60 * 1000,
  });

  const product = useQuery({
    queryKey: ['admin', 'product', id],
    queryFn: () => getData<ProductResponse>(`/admin/products/${id}`),
    enabled: !isNew,
  });

  const variants = useQuery({
    queryKey: ['admin', 'product', id, 'variants'],
    queryFn: () => getData<ProductVariantAdmin[]>(`/admin/products/${id}/variants`),
    enabled: !isNew,
  });

  // --- draft bootstrap ----------------------------------------------------

  useEffect(() => {
    if (draft) return;

    if (isNew) {
      setDraft({
        id: 0,
        sku: '',
        barcode: null,
        slug: '',
        categoryId: null,
        price: null,
        compareAtPrice: null,
        costPrice: null,
        currency: 'AZN',
        taxRate: 0,
        showPrice: true,
        purchasable: true,
        trackStock: false,
        stockQuantity: 0,
        lowStockThreshold: 5,
        allowBackorder: false,
        minOrderQuantity: 1,
        maxOrderQuantity: null,
        weightGrams: null,
        featured: false,
        isNew: false,
        status: 'DRAFT',
        sortOrder: 0,
        effectivePrice: {} as PriceInfo,
        translations: { az: { ...EMPTY_TRANSLATION } },
        images: [],
        documents: [],
        attributes: [],
      });
    } else if (product.data) {
      // The API leaves optional translation fields (seoTitle, description, ...) as null
      // when never filled in; TranslationDraft promises plain strings, so every field
      // this form reads .length or renders as a controlled input from is normalised here
      // once, rather than every read site needing its own `?? ''` guard.
      const cloned = structuredClone(product.data);
      for (const translation of Object.values(cloned.translations)) {
        translation.shortDescription ??= '';
        translation.description ??= '';
        translation.seoTitle ??= '';
        translation.seoDescription ??= '';
      }
      setDraft(cloned);
    }
  }, [draft, isNew, product.data]);

  const patch = (changes: Partial<ProductResponse>) => {
    setDraft((current) => (current ? { ...current, ...changes } : current));
    setDirty(true);
  };

  const patchTranslation = (language: string, changes: Partial<TranslationDraft>) => {
    setDraft((current) => {
      if (!current) return current;
      const existing = current.translations[language] ?? { ...EMPTY_TRANSLATION };
      return {
        ...current,
        translations: { ...current.translations, [language]: { ...existing, ...changes } },
      };
    });
    setDirty(true);
  };

  // --- mutations ----------------------------------------------------------

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'product', id] });
    queryClient.invalidateQueries({ queryKey: ['products'] });
  };

  const save = useMutation({
    mutationFn: (values: ProductResponse) => {
      const payload = {
        sku: values.sku,
        barcode: values.barcode || null,
        slug: values.slug || undefined,
        categoryId: values.categoryId,
        price: values.price,
        compareAtPrice: values.compareAtPrice,
        costPrice: values.costPrice,
        currency: values.currency,
        taxRate: values.taxRate,
        showPrice: values.showPrice,
        purchasable: values.purchasable,
        trackStock: values.trackStock,
        stockQuantity: values.stockQuantity,
        lowStockThreshold: values.lowStockThreshold,
        allowBackorder: values.allowBackorder,
        minOrderQuantity: values.minOrderQuantity,
        maxOrderQuantity: values.maxOrderQuantity,
        weightGrams: values.weightGrams,
        featured: values.featured,
        isNew: values.isNew,
        status: values.status,
        sortOrder: values.sortOrder,
        // Empty language tabs are dropped rather than sent as blank translations.
        translations: Object.fromEntries(
          Object.entries(values.translations).filter(([, translation]) =>
            translation.name.trim(),
          ),
        ),
        attributes: values.attributes,
      };
      return isNew
        ? postData<ProductResponse>('/admin/products', payload)
        : putData<ProductResponse>(`/admin/products/${id}`, payload);
    },
    onSuccess: (saved) => {
      invalidate();
      setDirty(false);
      toast.success(isNew ? 'Məhsul yaradıldı' : 'Məhsul yeniləndi');
      if (isNew) {
        navigate(`/admin/products/${saved.id}`, { replace: true });
        setDraft(saved);
      }
    },
    onError: (error: ApiError) => {
      const fields = Object.entries(error.fieldErrors);
      if (fields.length > 0) {
        toast.error(fields.map(([field, message]) => `${field}: ${message}`).join(' · '));
      } else {
        toast.error(error.message);
      }
    },
  });

  const addImages = useMutation({
    mutationFn: async (picked: PickedMedia[]) => {
      let latest: ProductImage[] = draft?.images ?? [];
      for (const media of picked) {
        latest = await postData<ProductImage[]>(`/admin/products/${id}/images`, {
          mediaId: media.id,
        });
      }
      return latest;
    },
    onSuccess: (images) => {
      setDraft((current) => (current ? { ...current, images } : current));
      invalidate();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const setMainImage = useMutation({
    mutationFn: (imageId: number) =>
      patchData<ProductImage[]>(`/admin/products/${id}/images/${imageId}/main`),
    onSuccess: (images) => setDraft((current) => (current ? { ...current, images } : current)),
    onError: (error: ApiError) => toast.error(error.message),
  });

  const removeImage = useMutation({
    mutationFn: (imageId: number) =>
      deleteRequest(`/admin/products/${id}/images/${imageId}`).then(() =>
        getData<ProductResponse>(`/admin/products/${id}`),
      ),
    onSuccess: (fresh) => setDraft((current) => (current ? { ...current, images: fresh.images } : current)),
    onError: (error: ApiError) => toast.error(error.message),
  });

  const addDocument = useMutation({
    mutationFn: (media: PickedMedia) =>
      postData<ProductDocument[]>(`/admin/products/${id}/documents`, {
        mediaId: media.id,
        title: media.originalName,
        documentType: 'DATASHEET',
      }),
    onSuccess: (documents) =>
      setDraft((current) => (current ? { ...current, documents } : current)),
    onError: (error: ApiError) => toast.error(error.message),
  });

  const removeDocument = useMutation({
    mutationFn: (documentId: number) =>
      deleteRequest(`/admin/products/${id}/documents/${documentId}`).then(() =>
        getData<ProductResponse>(`/admin/products/${id}`),
      ),
    onSuccess: (fresh) =>
      setDraft((current) => (current ? { ...current, documents: fresh.documents } : current)),
    onError: (error: ApiError) => toast.error(error.message),
  });

  interface VariantFormValues {
    sku: string;
    priceOverride: number | null;
    trackStock: boolean;
    stockQuantity: number;
    allowBackorder: boolean;
    isDefault: boolean;
    active: boolean;
    optionIds: number[];
  }

  const createVariant = useMutation({
    mutationFn: (values: VariantFormValues) =>
      postData<ProductVariantAdmin[]>(`/admin/products/${id}/variants`, values),
    onSuccess: (fresh) => {
      queryClient.setQueryData(['admin', 'product', id, 'variants'], fresh);
      setEditingVariant(null);
      toast.success('Variant yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const updateVariant = useMutation({
    mutationFn: ({ variantId, values }: { variantId: number; values: VariantFormValues }) =>
      putData<ProductVariantAdmin[]>(`/admin/products/${id}/variants/${variantId}`, values),
    onSuccess: (fresh) => {
      queryClient.setQueryData(['admin', 'product', id, 'variants'], fresh);
      setEditingVariant(null);
      toast.success('Variant yeniləndi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const deleteVariant = useMutation({
    mutationFn: (variantId: number) =>
      deleteRequest(`/admin/products/${id}/variants/${variantId}`).then(() =>
        getData<ProductVariantAdmin[]>(`/admin/products/${id}/variants`),
      ),
    onSuccess: (fresh) => {
      queryClient.setQueryData(['admin', 'product', id, 'variants'], fresh);
      toast.success('Variant silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const duplicate = useMutation({
    mutationFn: () => postData<{ id: number }>(`/admin/products/${id}/duplicate`),
    onSuccess: (copy) => {
      toast.success('Məhsul kopyalandı');
      setDraft(null);
      navigate(`/admin/products/${copy.id}`);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const quickDiscount = useMutation({
    mutationFn: (values: { name: string; type: string; value: number; endsAt: string }) =>
      postData(`/admin/discounts/for-product/${id}`, {
        name: values.name,
        discountType: values.type,
        value: values.value,
        scope: 'PRODUCTS',
        productIds: [Number(id)],
        endsAt: values.endsAt ? new Date(values.endsAt).toISOString() : null,
        status: 'ACTIVE',
        showBadge: true,
        badgeStyle: 'SALE',
        priority: 50,
      }),
    onSuccess: () => {
      invalidate();
      setDiscountPanelOpen(false);
      toast.success('Endirim kampaniyası yaradıldı və aktivləşdirildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  // --- render -------------------------------------------------------------

  if (!draft || (!isNew && product.isLoading)) {
    return <InlineLoader label="Məhsul yüklənir…" />;
  }

  const activeLanguages = languages.data ?? [{ code: 'az', name: 'Azerbaijani' } as Language];
  const translation = draft.translations[activeLanguage] ?? EMPTY_TRANSLATION;
  const defaultName = draft.translations['az']?.name;

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link
            to="/admin/products"
            className="inline-flex items-center gap-1.5 hover:text-[var(--color-heading)]"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            Məhsullar
          </Link>
        }
        title={isNew ? 'Yeni məhsul' : (defaultName || draft.sku || 'Məhsul')}
        description={!isNew ? `SKU: ${draft.sku}` : undefined}
        actions={
          <>
            {!isNew && (
              <>
                <Button
                  variant="secondary"
                  icon={<Copy size={15} aria-hidden="true" />}
                  loading={duplicate.isPending}
                  onClick={() => duplicate.mutate()}
                >
                  Kopyala
                </Button>
                <Button
                  variant="secondary"
                  icon={<Tag size={15} aria-hidden="true" />}
                  onClick={() => setDiscountPanelOpen((open) => !open)}
                >
                  Endirim əlavə et
                </Button>
              </>
            )}
            <Button
              loading={save.isPending}
              icon={<Save size={15} aria-hidden="true" />}
              onClick={() => save.mutate(draft)}
            >
              Yadda saxla
            </Button>
          </>
        }
      />

      {/* Quick single-product campaign, so a one-off discount does not require a
          trip to the campaigns screen. */}
      {discountPanelOpen && !isNew && (
        <QuickDiscountPanel
          productName={defaultName ?? draft.sku}
          currentPrice={draft.price}
          pending={quickDiscount.isPending}
          onCancel={() => setDiscountPanelOpen(false)}
          onSubmit={(values) => quickDiscount.mutate(values)}
        />
      )}

      {/* ---------------- tabs ---------------- */}
      <div className="mb-6 flex flex-wrap gap-1 border-b border-[var(--color-border)]">
        {TABS.map((entry) => {
          const Icon = entry.icon;
          const disabled = isNew && (entry.id === 'media' || entry.id === 'variants');
          return (
            <button
              key={entry.id}
              type="button"
              disabled={disabled}
              onClick={() => setTab(entry.id)}
              title={disabled ? 'Əvvəlcə məhsulu yadda saxlayın' : undefined}
              className={clsx(
                'flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors',
                'disabled:cursor-not-allowed disabled:opacity-40',
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

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          {/* ================= GENERAL ================= */}
          {tab === 'general' && (
            <>
              <Card
                title="Məzmun"
                description="Hər dil üçün ayrıca doldurulur. Əsas dil (az) mütləqdir."
                actions={
                  <div className="flex gap-1">
                    {activeLanguages.map((language) => {
                      const filled = Boolean(draft.translations[language.code]?.name?.trim());
                      return (
                        <button
                          key={language.code}
                          type="button"
                          onClick={() => setActiveLanguage(language.code)}
                          className={clsx(
                            'rounded-[var(--radius-button)] px-3 py-1.5 text-xs font-semibold uppercase transition-colors',
                            activeLanguage === language.code
                              ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                              : 'text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                          )}
                        >
                          {language.code}
                          {/* A quiet dot beats a red error: an empty secondary language
                              is normal, not a mistake. */}
                          {!filled && language.code !== 'az' && (
                            <span className="ms-1 opacity-50">·</span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                }
              >
                <div className="space-y-4">
                  <Field
                    label="Ad"
                    htmlFor="name"
                    required={activeLanguage === 'az'}
                  >
                    <Input
                      id="name"
                      value={translation.name}
                      onChange={(event) =>
                        patchTranslation(activeLanguage, { name: event.target.value })
                      }
                    />
                  </Field>

                  <Field label="Qısa təsvir" htmlFor="shortDescription" hint="Kataloq kartında görünür">
                    <Textarea
                      id="shortDescription"
                      rows={2}
                      value={translation.shortDescription}
                      onChange={(event) =>
                        patchTranslation(activeLanguage, { shortDescription: event.target.value })
                      }
                    />
                  </Field>

                  <Field
                    label="Ətraflı təsvir"
                    htmlFor="description"
                    hint="HTML dəstəklənir və serverdə təhlükəsizlik üçün təmizlənir"
                  >
                    <Textarea
                      id="description"
                      rows={8}
                      value={translation.description}
                      onChange={(event) =>
                        patchTranslation(activeLanguage, { description: event.target.value })
                      }
                    />
                  </Field>
                </div>
              </Card>

              <Card title="Təsnifat">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="SKU" htmlFor="sku" required hint="Unikal məhsul kodu">
                    <Input
                      id="sku"
                      value={draft.sku}
                      onChange={(event) => patch({ sku: event.target.value })}
                    />
                  </Field>

                  <Field label="Barkod" htmlFor="barcode">
                    <Input
                      id="barcode"
                      value={draft.barcode ?? ''}
                      onChange={(event) => patch({ barcode: event.target.value || null })}
                    />
                  </Field>

                  <Field label="Kateqoriya" htmlFor="categoryId">
                    <Select
                      id="categoryId"
                      value={draft.categoryId ?? ''}
                      onChange={(event) =>
                        patch({ categoryId: event.target.value ? Number(event.target.value) : null })
                      }
                    >
                      <option value="">Seçilməyib</option>
                      {(categories.data ?? []).map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </Select>
                  </Field>

                  <Field
                    label="Slug"
                    htmlFor="slug"
                    hint="Boş qoyulsa addan yaradılır"
                  >
                    <Input
                      id="slug"
                      value={draft.slug}
                      onChange={(event) => patch({ slug: event.target.value })}
                      placeholder="avtomatik"
                    />
                  </Field>
                </div>
              </Card>
            </>
          )}

          {/* ================= PRICING ================= */}
          {tab === 'pricing' && (
            <>
              <Card title="Qiymət">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field
                    label="Kataloq qiyməti"
                    htmlFor="price"
                    hint="Endirimsiz qiymət. Kampaniyalar bunun üzərindən hesablanır."
                  >
                    <Input
                      id="price"
                      type="number"
                      step="0.01"
                      min="0"
                      value={draft.price ?? ''}
                      onChange={(event) =>
                        patch({ price: event.target.value ? Number(event.target.value) : null })
                      }
                    />
                  </Field>

                  <Field
                    label="Müqayisə qiyməti"
                    htmlFor="compareAtPrice"
                    hint="Kampaniya olmadıqda göstərilən “köhnə qiymət”"
                  >
                    <Input
                      id="compareAtPrice"
                      type="number"
                      step="0.01"
                      min="0"
                      value={draft.compareAtPrice ?? ''}
                      onChange={(event) =>
                        patch({
                          compareAtPrice: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                    />
                  </Field>

                  <Field label="Maya dəyəri" htmlFor="costPrice" hint="Yalnız daxili hesabat üçün">
                    <Input
                      id="costPrice"
                      type="number"
                      step="0.01"
                      min="0"
                      value={draft.costPrice ?? ''}
                      onChange={(event) =>
                        patch({ costPrice: event.target.value ? Number(event.target.value) : null })
                      }
                    />
                  </Field>

                  <Field label="ƏDV dərəcəsi (%)" htmlFor="taxRate">
                    <Input
                      id="taxRate"
                      type="number"
                      step="0.01"
                      min="0"
                      value={draft.taxRate}
                      onChange={(event) => patch({ taxRate: Number(event.target.value) })}
                    />
                  </Field>
                </div>

                <div className="mt-5 space-y-4 border-t border-[var(--color-border)] pt-5">
                  <Toggle
                    label="Qiyməti göstər"
                    hint="Söndürülsə saytda “Qiymət üçün müraciət edin” yazılır və məhsul səbətə atıla bilmir"
                    checked={draft.showPrice}
                    onChange={(checked) => patch({ showPrice: checked })}
                  />
                  <Toggle
                    label="Satışa açıq"
                    hint="Söndürülsə məhsul kataloqda görünür, amma səbətə atıla bilmir"
                    checked={draft.purchasable}
                    onChange={(checked) => patch({ purchasable: checked })}
                  />
                </div>
              </Card>

              <Card title="Anbar">
                <Toggle
                  label="Anbarı izlə"
                  hint="Söndürülsə məhsul həmişə “stokda var” sayılır"
                  checked={draft.trackStock}
                  onChange={(checked) => patch({ trackStock: checked })}
                />

                {draft.trackStock && (
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <Field label="Anbar sayı" htmlFor="stockQuantity">
                      <Input
                        id="stockQuantity"
                        type="number"
                        min="0"
                        value={draft.stockQuantity}
                        onChange={(event) => patch({ stockQuantity: Number(event.target.value) })}
                      />
                    </Field>

                    <Field
                      label="Azalma həddi"
                      htmlFor="lowStockThreshold"
                      hint="Bu saydan az qalanda dashboard-da xəbərdarlıq çıxır"
                    >
                      <Input
                        id="lowStockThreshold"
                        type="number"
                        min="0"
                        value={draft.lowStockThreshold}
                        onChange={(event) =>
                          patch({ lowStockThreshold: Number(event.target.value) })
                        }
                      />
                    </Field>

                    <div className="sm:col-span-2">
                      <Toggle
                        label="Anbar bitəndə sifarişə icazə ver"
                        hint="Backorder — məhsul “sifarişlə gətirilir” kimi göstərilir"
                        checked={draft.allowBackorder}
                        onChange={(checked) => patch({ allowBackorder: checked })}
                      />
                    </div>
                  </div>
                )}

                <div className="mt-5 grid gap-4 border-t border-[var(--color-border)] pt-5 sm:grid-cols-3">
                  <Field label="Min. sifariş sayı" htmlFor="minOrderQuantity">
                    <Input
                      id="minOrderQuantity"
                      type="number"
                      min="1"
                      value={draft.minOrderQuantity}
                      onChange={(event) => patch({ minOrderQuantity: Number(event.target.value) })}
                    />
                  </Field>

                  <Field label="Maks. sifariş sayı" htmlFor="maxOrderQuantity" hint="Boş = limitsiz">
                    <Input
                      id="maxOrderQuantity"
                      type="number"
                      min="1"
                      value={draft.maxOrderQuantity ?? ''}
                      onChange={(event) =>
                        patch({
                          maxOrderQuantity: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                    />
                  </Field>

                  <Field label="Çəki (qram)" htmlFor="weightGrams">
                    <Input
                      id="weightGrams"
                      type="number"
                      min="0"
                      value={draft.weightGrams ?? ''}
                      onChange={(event) =>
                        patch({ weightGrams: event.target.value ? Number(event.target.value) : null })
                      }
                    />
                  </Field>
                </div>
              </Card>
            </>
          )}

          {/* ================= MEDIA ================= */}
          {tab === 'media' && !isNew && (
            <>
              <Card
                title="Şəkillər"
                description="İlk şəkil əsas şəkil kimi istifadə olunur"
                actions={
                  <Button
                    size="sm"
                    variant="secondary"
                    icon={<ImagePlus size={14} aria-hidden="true" />}
                    onClick={() => setImagePickerOpen(true)}
                  >
                    Şəkil əlavə et
                  </Button>
                }
              >
                {draft.images.length === 0 ? (
                  <p className="py-8 text-center text-sm text-[var(--color-subtle)]">
                    Hələ şəkil yoxdur
                  </p>
                ) : (
                  <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
                    {draft.images.map((image) => (
                      <div
                        key={image.id}
                        className={clsx(
                          'group relative overflow-hidden rounded-[var(--radius-button)] border-2',
                          image.main ? 'border-[var(--color-primary)]' : 'border-[var(--color-border)]',
                        )}
                      >
                        <div className="aspect-square bg-[var(--color-muted)]">
                          <img
                            src={image.url}
                            alt={image.alt ?? ''}
                            className="h-full w-full object-cover"
                          />
                        </div>

                        {image.main && (
                          <span className="absolute start-1.5 top-1.5 rounded-full bg-[var(--color-primary)] px-2 py-0.5 text-[0.625rem] font-bold text-[var(--color-primary-contrast)]">
                            Əsas
                          </span>
                        )}

                        <div className="absolute inset-x-0 bottom-0 flex justify-center gap-1 bg-black/60 p-1.5 opacity-0 transition-opacity group-hover:opacity-100">
                          {!image.main && (
                            <button
                              type="button"
                              title="Əsas şəkil et"
                              onClick={() => setMainImage.mutate(image.id)}
                              className="rounded p-1 text-white hover:bg-white/20"
                            >
                              <Star size={14} aria-hidden="true" />
                            </button>
                          )}
                          <button
                            type="button"
                            title="Sil"
                            onClick={() => removeImage.mutate(image.id)}
                            className="rounded p-1 text-white hover:bg-white/20"
                          >
                            <Trash2 size={14} aria-hidden="true" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Card>

              <Card
                title="Sənədlər"
                description="Kataloq, təlimat, sertifikat"
                actions={
                  <Button
                    size="sm"
                    variant="secondary"
                    icon={<FileText size={14} aria-hidden="true" />}
                    onClick={() => setDocumentPickerOpen(true)}
                  >
                    Sənəd əlavə et
                  </Button>
                }
              >
                {draft.documents.length === 0 ? (
                  <p className="py-8 text-center text-sm text-[var(--color-subtle)]">
                    Hələ sənəd yoxdur
                  </p>
                ) : (
                  <ul className="divide-y divide-[var(--color-border)]">
                    {draft.documents.map((document) => (
                      <li key={document.id} className="flex items-center gap-3 py-2.5">
                        <FileText size={16} className="text-[var(--color-primary)]" aria-hidden="true" />
                        <span className="min-w-0 flex-1 truncate text-sm text-[var(--color-heading)]">
                          {document.title}
                        </span>
                        <span className="text-xs text-[var(--color-subtle)]">
                          {formatFileSize(document.fileSize)}
                        </span>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-[var(--color-danger)]"
                          onClick={() => removeDocument.mutate(document.id)}
                          icon={<Trash2 size={14} aria-hidden="true" />}
                        />
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </>
          )}

          {/* ================= VARIANTS ================= */}
          {tab === 'variants' && !isNew && (
            <>
              <Card
                title="Variantlar"
                description="Rəng, ölçü və s. üzrə ayrı SKU və anbar sayı olan seçimlər"
                actions={
                  <Button
                    size="sm"
                    variant="secondary"
                    icon={<Plus size={14} aria-hidden="true" />}
                    onClick={() => setEditingVariant('new')}
                  >
                    Variant əlavə et
                  </Button>
                }
              >
                {variants.isLoading ? (
                  <InlineLoader label="Variantlar yüklənir…" />
                ) : !variants.data || variants.data.length === 0 ? (
                  <p className="py-8 text-center text-sm text-[var(--color-subtle)]">
                    Bu məhsulun hələ variantı yoxdur. Variant yoxdursa, məhsul bir vahid kimi satılır.
                  </p>
                ) : (
                  <ul className="divide-y divide-[var(--color-border)]">
                    {variants.data.map((variant) => (
                      <li key={variant.id} className="flex flex-wrap items-center gap-3 py-3">
                        {variant.imageUrl ? (
                          <img
                            src={variant.imageUrl}
                            alt=""
                            className="h-11 w-11 shrink-0 rounded-[var(--radius-input)] object-cover"
                          />
                        ) : (
                          <div className="h-11 w-11 shrink-0 rounded-[var(--radius-input)] bg-[var(--color-muted)]" />
                        )}
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-medium text-[var(--color-heading)]">
                              {variant.optionLabel}
                            </span>
                            {variant.isDefault && <Badge tone="info">Standart</Badge>}
                            {!variant.active && <Badge tone="neutral">Deaktiv</Badge>}
                          </div>
                          <p className="text-xs text-[var(--color-subtle)]">
                            SKU: {variant.sku}
                            {variant.priceOverride !== null && ` · ${formatPrice(variant.priceOverride)}`}
                            {' · '}
                            {variant.trackStock ? `${variant.stockQuantity} ədəd anbarda` : 'Anbar izlənmir'}
                          </p>
                        </div>
                        <Button variant="ghost" size="sm" onClick={() => setEditingVariant(variant)}>
                          Redaktə
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-[var(--color-danger)]"
                          onClick={() => {
                            if (window.confirm('Bu variantı silmək istədiyinizə əminsiniz?')) {
                              deleteVariant.mutate(variant.id);
                            }
                          }}
                          icon={<Trash2 size={14} aria-hidden="true" />}
                        />
                      </li>
                    ))}
                  </ul>
                )}
              </Card>

              {editingVariant && (
                <VariantFormPanel
                  attributes={(attributes.data ?? []).filter(
                    (attribute) => attribute.dataType === 'SELECT' || attribute.dataType === 'MULTI_SELECT',
                  )}
                  initial={editingVariant === 'new' ? null : editingVariant}
                  pending={createVariant.isPending || updateVariant.isPending}
                  onCancel={() => setEditingVariant(null)}
                  onSubmit={(values) => {
                    if (editingVariant === 'new') {
                      createVariant.mutate(values);
                    } else {
                      updateVariant.mutate({ variantId: editingVariant.id, values });
                    }
                  }}
                />
              )}
            </>
          )}

          {/* ================= SPECIFICATIONS ================= */}
          {tab === 'specs' && (
            <Card
              title="Xüsusiyyətlər"
              description="Məhsul detalı səhifəsindəki texniki cədvəl"
            >
              {!attributes.data || attributes.data.length === 0 ? (
                <p className="py-8 text-center text-sm text-[var(--color-subtle)]">
                  Hələ attribut təyin edilməyib.
                </p>
              ) : (
                <div className="space-y-4">
                  {attributes.data.map((attribute) => (
                    <AttributeInput
                      key={attribute.id}
                      attribute={attribute}
                      value={draft.attributes.find((v) => v.attributeId === attribute.id)}
                      allValues={draft.attributes}
                      onChange={(next) => {
                        patch({
                          attributes: [
                            ...draft.attributes.filter((v) => v.attributeId !== attribute.id),
                            ...next,
                          ],
                        });
                      }}
                    />
                  ))}
                </div>
              )}
            </Card>
          )}

          {/* ================= SEO ================= */}
          {tab === 'seo' && (
            <Card
              title="SEO"
              description={`${activeLanguage.toUpperCase()} dili üçün. Boş qoyulsa məhsul adı və qısa təsvir istifadə olunur.`}
              actions={
                <div className="flex gap-1">
                  {activeLanguages.map((language) => (
                    <button
                      key={language.code}
                      type="button"
                      onClick={() => setActiveLanguage(language.code)}
                      className={clsx(
                        'rounded-[var(--radius-button)] px-3 py-1.5 text-xs font-semibold uppercase transition-colors',
                        activeLanguage === language.code
                          ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                          : 'text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                      )}
                    >
                      {language.code}
                    </button>
                  ))}
                </div>
              }
            >
              <div className="space-y-4">
                <Field
                  label="SEO başlığı"
                  htmlFor="seoTitle"
                  hint={`${translation.seoTitle.length}/60 simvol — Google ~60 simvol göstərir`}
                >
                  <Input
                    id="seoTitle"
                    maxLength={255}
                    value={translation.seoTitle}
                    onChange={(event) =>
                      patchTranslation(activeLanguage, { seoTitle: event.target.value })
                    }
                    placeholder={translation.name}
                  />
                </Field>

                <Field
                  label="SEO təsviri"
                  htmlFor="seoDescription"
                  hint={`${translation.seoDescription.length}/160 simvol`}
                >
                  <Textarea
                    id="seoDescription"
                    rows={3}
                    maxLength={500}
                    value={translation.seoDescription}
                    onChange={(event) =>
                      patchTranslation(activeLanguage, { seoDescription: event.target.value })
                    }
                    placeholder={translation.shortDescription}
                  />
                </Field>

                {/* Search result preview: the SEO fields are otherwise abstract. */}
                <div className="rounded-[var(--radius-button)] border border-[var(--color-border)] bg-[var(--color-muted)] p-4">
                  <p className="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
                    Axtarış nəticəsində belə görünəcək
                  </p>
                  <p className="text-xs text-[var(--color-success)]">
                    /mehsul/{draft.slug || 'mehsul-adi'}
                  </p>
                  <p className="mt-0.5 text-base text-[#1a0dab]">
                    {translation.seoTitle || translation.name || 'Məhsul adı'}
                  </p>
                  <p className="mt-0.5 line-clamp-2 text-xs text-[var(--color-body)]">
                    {translation.seoDescription ||
                      translation.shortDescription ||
                      'Təsvir daxil edilməyib.'}
                  </p>
                </div>
              </div>
            </Card>
          )}
        </div>

        {/* ---------------- side ---------------- */}
        <div className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          <Card title="Dərc">
            <Field label="Status" htmlFor="status">
              <Select
                id="status"
                value={draft.status}
                onChange={(event) =>
                  patch({ status: event.target.value as ProductResponse['status'] })
                }
              >
                <option value="DRAFT">Qaralama — saytda görünmür</option>
                <option value="ACTIVE">Aktiv — saytda görünür</option>
                <option value="ARCHIVED">Arxiv</option>
              </Select>
            </Field>

            <div className="mt-4 space-y-4">
              <Toggle
                label="Seçilmiş"
                hint="Ana səhifədə göstərilir"
                checked={draft.featured}
                onChange={(checked) => patch({ featured: checked })}
              />
              <Toggle
                label="Yeni"
                hint="Kartda “Yeni” nişanı"
                checked={draft.isNew}
                onChange={(checked) => patch({ isNew: checked })}
              />
            </div>

            <Field label="Sıra nömrəsi" htmlFor="sortOrder" className="mt-4">
              <Input
                id="sortOrder"
                type="number"
                value={draft.sortOrder}
                onChange={(event) => patch({ sortOrder: Number(event.target.value) })}
              />
            </Field>

            {dirty && (
              <p className="mt-4 rounded-[var(--radius-button)] bg-[var(--color-warning)]/10 px-3 py-2 text-xs text-[var(--color-warning)]">
                Yadda saxlanmamış dəyişikliklər var.
              </p>
            )}
          </Card>

          {/* Live campaign on this product, if any. */}
          {!isNew && draft.effectivePrice?.discounted && (
            <Card title="Aktiv endirim">
              <div className="flex items-baseline gap-2">
                <span className="price-now text-xl">
                  {formatPrice(draft.effectivePrice.finalPrice)}
                </span>
                <span className="price-was text-sm">
                  {formatPrice(draft.effectivePrice.listPrice)}
                </span>
              </div>
              <p className="mt-2 text-sm text-[var(--color-body)]">
                {draft.effectivePrice.discountName}
              </p>
              <Badge tone="sale" className="mt-2">
                {draft.effectivePrice.badgeLabel}
              </Badge>
              <Link
                to={`/admin/discounts/${draft.effectivePrice.discountId}`}
                className="mt-3 block text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                Kampaniyaya bax
              </Link>
            </Card>
          )}

          {!isNew && (
            <Card title="Saytda">
              <a
                href={`/mehsul/${draft.slug}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                Məhsul səhifəsini aç
              </a>
            </Card>
          )}
        </div>
      </div>

      <MediaPicker
        open={imagePickerOpen}
        multiple
        type="IMAGE"
        excludeIds={draft.images.map((image) => image.mediaId)}
        onClose={() => setImagePickerOpen(false)}
        onSelect={(picked) => addImages.mutate(picked)}
      />

      <MediaPicker
        open={documentPickerOpen}
        type="DOCUMENT"
        onClose={() => setDocumentPickerOpen(false)}
        onSelect={(picked) => picked[0] && addDocument.mutate(picked[0])}
      />
    </>
  );
}

/**
 * Renders the right control for an attribute's data type, and emits the rows the API
 * expects. MULTI_SELECT produces one row per selected option — never a JSON array,
 * which is what keeps the values queryable for facet filtering.
 */
function AttributeInput({
  attribute,
  value,
  allValues,
  onChange,
}: {
  attribute: AttributeDefinition;
  value: AttributeValueDraft | undefined;
  allValues: AttributeValueDraft[];
  onChange: (next: AttributeValueDraft[]) => void;
}) {
  const base: AttributeValueDraft = {
    attributeId: attribute.id,
    optionId: null,
    textValue: null,
    numberValue: null,
    booleanValue: null,
  };

  const selectedOptionIds = useMemo(
    () =>
      allValues
        .filter((candidate) => candidate.attributeId === attribute.id && candidate.optionId)
        .map((candidate) => candidate.optionId as number),
    [allValues, attribute.id],
  );

  const label = `${attribute.name}${attribute.unit ? ` (${attribute.unit})` : ''}`;

  switch (attribute.dataType) {
    case 'TEXT':
      return (
        <Field label={label} htmlFor={`attr-${attribute.id}`} required={attribute.required}>
          <Input
            id={`attr-${attribute.id}`}
            value={value?.textValue ?? ''}
            onChange={(event) =>
              onChange(event.target.value ? [{ ...base, textValue: event.target.value }] : [])
            }
          />
        </Field>
      );

    case 'NUMBER':
      return (
        <Field label={label} htmlFor={`attr-${attribute.id}`} required={attribute.required}>
          <Input
            id={`attr-${attribute.id}`}
            type="number"
            step="any"
            value={value?.numberValue ?? ''}
            onChange={(event) =>
              onChange(
                event.target.value ? [{ ...base, numberValue: Number(event.target.value) }] : [],
              )
            }
          />
        </Field>
      );

    case 'BOOLEAN':
      return (
        <Toggle
          label={label}
          checked={value?.booleanValue === true}
          onChange={(checked) => onChange([{ ...base, booleanValue: checked }])}
        />
      );

    case 'SELECT':
      return (
        <Field label={label} htmlFor={`attr-${attribute.id}`} required={attribute.required}>
          <Select
            id={`attr-${attribute.id}`}
            value={value?.optionId ?? ''}
            onChange={(event) =>
              onChange(event.target.value ? [{ ...base, optionId: Number(event.target.value) }] : [])
            }
          >
            <option value="">Seçilməyib</option>
            {attribute.options.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      );

    case 'MULTI_SELECT':
      return (
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-[var(--color-heading)]">{label}</legend>
          <div className="flex flex-wrap gap-2">
            {attribute.options.map((option) => {
              const checked = selectedOptionIds.includes(option.id);
              return (
                <label
                  key={option.id}
                  className={clsx(
                    'flex cursor-pointer items-center gap-2 rounded-[var(--radius-button)] border px-3 py-1.5 text-sm transition-colors',
                    checked
                      ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/8 text-[var(--color-primary)]'
                      : 'border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                  )}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    className="sr-only"
                    onChange={(event) => {
                      const next = event.target.checked
                        ? [...selectedOptionIds, option.id]
                        : selectedOptionIds.filter((current) => current !== option.id);
                      onChange(next.map((optionId) => ({ ...base, optionId })));
                    }}
                  />
                  {option.colorHex && (
                    <span
                      className="h-3.5 w-3.5 rounded-full border border-black/10"
                      style={{ backgroundColor: option.colorHex }}
                      aria-hidden="true"
                    />
                  )}
                  {option.label}
                </label>
              );
            })}
          </div>
        </fieldset>
      );

    default:
      return null;
  }
}

/** One-off campaign for a single product, created without leaving the editor. */
function QuickDiscountPanel({
  productName,
  currentPrice,
  pending,
  onCancel,
  onSubmit,
}: {
  productName: string;
  currentPrice: number | null;
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: { name: string; type: string; value: number; endsAt: string }) => void;
}) {
  const [type, setType] = useState('PERCENTAGE');
  const [value, setValue] = useState(10);
  const [endsAt, setEndsAt] = useState('');

  const resulting = useMemo(() => {
    if (currentPrice === null) return null;
    const saving = type === 'PERCENTAGE' ? (currentPrice * value) / 100 : value;
    return Math.max(currentPrice - saving, 0);
  }, [currentPrice, type, value]);

  return (
    <Card
      title="Sürətli endirim"
      description="Yalnız bu məhsul üçün kampaniya yaradır və dərhal aktivləşdirir"
      className="mb-6 border-[var(--color-sale)]/40"
      actions={
        <Button variant="ghost" size="sm" onClick={onCancel}>
          Bağla
        </Button>
      }
    >
      <div className="grid gap-4 sm:grid-cols-4">
        <Field label="Tip" htmlFor="quickType">
          <Select id="quickType" value={type} onChange={(event) => setType(event.target.value)}>
            <option value="PERCENTAGE">Faiz (%)</option>
            <option value="FIXED_AMOUNT">Məbləğ (₼)</option>
          </Select>
        </Field>

        <Field label="Dəyər" htmlFor="quickValue">
          <Input
            id="quickValue"
            type="number"
            min="0.01"
            step="0.01"
            max={type === 'PERCENTAGE' ? 100 : undefined}
            value={value}
            onChange={(event) => setValue(Number(event.target.value))}
          />
        </Field>

        <Field label="Bitmə tarixi" htmlFor="quickEndsAt" hint="Boş = müddətsiz">
          <Input
            id="quickEndsAt"
            type="datetime-local"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </Field>

        <div className="flex flex-col justify-end">
          {resulting !== null && (
            <p className="mb-2 text-sm">
              <span className="text-[var(--color-subtle)]">Yeni qiymət: </span>
              <span className="font-bold tabular text-[var(--color-sale)]">
                {formatPrice(resulting)}
              </span>
            </p>
          )}
          <Button
            loading={pending}
            disabled={value <= 0}
            onClick={() =>
              onSubmit({
                name: `${productName} — endirim`,
                type,
                value,
                endsAt,
              })
            }
          >
            Yarat və aktivləşdir
          </Button>
        </div>
      </div>
    </Card>
  );
}

/**
 * Add/edit one variant. Attributes are shown as single-select chip groups — a variant
 * cannot hold two options from the same attribute, so this UI can never produce an
 * invalid combination for the server to reject. An attribute the admin doesn't touch
 * simply isn't part of this variant's identity, matching the backend's own rule that
 * only the options a variant is actually built from count as its "axes".
 */
function VariantFormPanel({
  attributes,
  initial,
  pending,
  onCancel,
  onSubmit,
}: {
  attributes: AttributeDefinition[];
  initial: ProductVariantAdmin | null;
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: {
    sku: string;
    priceOverride: number | null;
    trackStock: boolean;
    stockQuantity: number;
    allowBackorder: boolean;
    isDefault: boolean;
    active: boolean;
    optionIds: number[];
  }) => void;
}) {
  const optionToAttribute = useMemo(() => {
    const map = new Map<number, number>();
    attributes.forEach((attribute) => attribute.options.forEach((option) => map.set(option.id, attribute.id)));
    return map;
  }, [attributes]);

  const [sku, setSku] = useState(initial?.sku ?? '');
  const [priceOverride, setPriceOverride] = useState<string>(
    initial?.priceOverride != null ? String(initial.priceOverride) : '',
  );
  const [trackStock, setTrackStock] = useState(initial?.trackStock ?? true);
  const [stockQuantity, setStockQuantity] = useState(initial?.stockQuantity ?? 0);
  const [allowBackorder, setAllowBackorder] = useState(initial?.allowBackorder ?? false);
  const [isDefault, setIsDefault] = useState(initial?.isDefault ?? false);
  const [active, setActive] = useState(initial?.active ?? true);
  const [selectedOptions, setSelectedOptions] = useState<Record<number, number>>(() => {
    const map: Record<number, number> = {};
    initial?.optionIds.forEach((optionId) => {
      const attributeId = optionToAttribute.get(optionId);
      if (attributeId !== undefined) {
        map[attributeId] = optionId;
      }
    });
    return map;
  });

  const optionIds = Object.values(selectedOptions);
  const valid = sku.trim().length > 0 && optionIds.length > 0;

  return (
    <Card
      title={initial ? 'Variantı redaktə et' : 'Yeni variant'}
      className="mt-6 border-[var(--color-primary)]/40"
      actions={
        <Button variant="ghost" size="sm" onClick={onCancel}>
          Bağla
        </Button>
      }
    >
      <div className="space-y-5">
        {attributes.length === 0 ? (
          <p className="text-sm text-[var(--color-subtle)]">
            Variant üçün seçim siyahılı (SELECT) attribut yoxdur. Əvvəlcə "Attributlar" bölməsində
            Rəng, Ölçü kimi bir attribut yaradın.
          </p>
        ) : (
          attributes.map((attribute) => (
            <div key={attribute.id}>
              <p className="mb-2 text-sm font-semibold text-[var(--color-heading)]">{attribute.name}</p>
              <div className="flex flex-wrap gap-2">
                {attribute.options.map((option) => {
                  const isActive = selectedOptions[attribute.id] === option.id;
                  return (
                    <button
                      key={option.id}
                      type="button"
                      onClick={() =>
                        setSelectedOptions((current) => {
                          if (current[attribute.id] === option.id) {
                            const next = { ...current };
                            delete next[attribute.id];
                            return next;
                          }
                          return { ...current, [attribute.id]: option.id };
                        })
                      }
                      className={clsx(
                        'flex items-center gap-1.5 rounded-[var(--radius-button)] border px-3 py-1.5 text-sm font-medium transition-colors',
                        isActive
                          ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                          : 'border-[var(--color-border)] text-[var(--color-body)] hover:border-[var(--color-primary)]/50',
                      )}
                    >
                      {option.colorHex && (
                        <span
                          className="h-3.5 w-3.5 rounded-full border border-black/10"
                          style={{ backgroundColor: option.colorHex }}
                          aria-hidden="true"
                        />
                      )}
                      {option.label}
                    </button>
                  );
                })}
              </div>
            </div>
          ))
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="SKU" htmlFor="variantSku" required>
            <Input id="variantSku" value={sku} onChange={(event) => setSku(event.target.value)} />
          </Field>
          <Field label="Xüsusi qiymət" htmlFor="variantPrice" hint="Boş = məhsulun öz qiyməti">
            <Input
              id="variantPrice"
              type="number"
              min="0"
              step="0.01"
              value={priceOverride}
              onChange={(event) => setPriceOverride(event.target.value)}
            />
          </Field>
          <Field label="Anbar sayı" htmlFor="variantStock">
            <Input
              id="variantStock"
              type="number"
              min="0"
              value={stockQuantity}
              onChange={(event) => setStockQuantity(Number(event.target.value))}
              disabled={!trackStock}
            />
          </Field>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Toggle label="Anbar izlənsin" checked={trackStock} onChange={setTrackStock} />
          <Toggle
            label="Bitəndə sifariş qəbul et"
            hint="Backorder"
            checked={allowBackorder}
            onChange={setAllowBackorder}
          />
          <Toggle label="Standart variant" checked={isDefault} onChange={setIsDefault} />
          <Toggle label="Aktiv" checked={active} onChange={setActive} />
        </div>

        <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button variant="secondary" onClick={onCancel}>
            Ləğv et
          </Button>
          <Button
            loading={pending}
            disabled={!valid}
            onClick={() =>
              onSubmit({
                sku: sku.trim(),
                priceOverride: priceOverride.trim() === '' ? null : Number(priceOverride),
                trackStock,
                stockQuantity,
                allowBackorder,
                isDefault,
                active,
                optionIds,
              })
            }
          >
            {initial ? 'Yadda saxla' : 'Yarat'}
          </Button>
        </div>
      </div>
    </Card>
  );
}
