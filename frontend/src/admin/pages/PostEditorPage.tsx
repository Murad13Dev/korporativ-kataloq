import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { ArrowLeft, CalendarClock, ImagePlus, Save } from 'lucide-react';
import {
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
import { ApiError, getData, postData, putData } from '@/lib/api';
import type { Language } from '@/types/api';

type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface TranslationDraft {
  title: string;
  shortDescription: string;
  content: string;
  seoTitle: string;
  seoDescription: string;
}

interface PostResponse {
  id: number;
  slug: string;
  categoryId: number | null;
  coverMediaId: number | null;
  coverUrl: string | null;
  status: Status;
  featured: boolean;
  readingMinutes: number | null;
  viewCount: number;
  publishedAt: string | null;
  tagIds: number[];
  translations: Record<string, TranslationDraft>;
}

interface BlogCategory {
  id: number;
  name: string;
}

interface TagDto {
  id: number;
  slug: string;
  name: string;
}

const EMPTY_TRANSLATION: TranslationDraft = {
  title: '',
  shortDescription: '',
  content: '',
  seoTitle: '',
  seoDescription: '',
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
 * Post editor.
 *
 * The body is a plain textarea accepting HTML rather than a WYSIWYG. That is a
 * deliberate scope decision: a rich-text editor is a project of its own, and the server
 * sanitises whatever arrives anyway, so a paste-from-Word disaster cannot reach the site.
 */
export function PostEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [draft, setDraft] = useState<PostResponse | null>(null);
  const [language, setLanguage] = useState('az');
  const [dirty, setDirty] = useState(false);
  const [coverPickerOpen, setCoverPickerOpen] = useState(false);
  const [publishedAtInput, setPublishedAtInput] = useState('');

  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });

  const categories = useQuery({
    queryKey: ['admin', 'post-categories'],
    queryFn: () => getData<BlogCategory[]>('/admin/post-categories'),
    staleTime: 5 * 60 * 1000,
  });

  const tags = useQuery({
    queryKey: ['admin', 'tags'],
    queryFn: () => getData<TagDto[]>('/admin/tags'),
    staleTime: 5 * 60 * 1000,
  });

  const post = useQuery({
    queryKey: ['admin', 'post', id],
    queryFn: () => getData<PostResponse>(`/admin/posts/${id}`),
    enabled: !isNew,
  });

  useEffect(() => {
    if (draft) return;

    if (isNew) {
      setDraft({
        id: 0,
        slug: '',
        categoryId: null,
        coverMediaId: null,
        coverUrl: null,
        status: 'DRAFT',
        featured: false,
        readingMinutes: null,
        viewCount: 0,
        publishedAt: null,
        tagIds: [],
        translations: { az: { ...EMPTY_TRANSLATION } },
      });
    } else if (post.data) {
      setDraft(structuredClone(post.data));
      setPublishedAtInput(toLocalInput(post.data.publishedAt));
    }
  }, [draft, isNew, post.data]);

  const patch = (changes: Partial<PostResponse>) => {
    setDraft((current) => (current ? { ...current, ...changes } : current));
    setDirty(true);
  };

  const patchTranslation = (code: string, changes: Partial<TranslationDraft>) => {
    setDraft((current) => {
      if (!current) return current;
      const existing = current.translations[code] ?? { ...EMPTY_TRANSLATION };
      return {
        ...current,
        translations: { ...current.translations, [code]: { ...existing, ...changes } },
      };
    });
    setDirty(true);
  };

  const save = useMutation({
    mutationFn: (values: PostResponse) => {
      const payload = {
        slug: values.slug || undefined,
        categoryId: values.categoryId,
        coverMediaId: values.coverMediaId,
        status: values.status,
        featured: values.featured,
        readingMinutes: values.readingMinutes,
        publishedAt: toIso(publishedAtInput),
        tagIds: values.tagIds,
        translations: Object.fromEntries(
          Object.entries(values.translations).filter(([, translation]) =>
            translation.title.trim(),
          ),
        ),
      };
      return isNew
        ? postData<PostResponse>('/admin/posts', payload)
        : putData<PostResponse>(`/admin/posts/${id}`, payload);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'posts'] });
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      setDirty(false);
      toast.success(isNew ? 'Yazı yaradıldı' : 'Yazı yeniləndi');
      if (isNew) {
        navigate(`/admin/posts/${saved.id}`, { replace: true });
        setDraft(saved);
      }
    },
    onError: (error: ApiError) => {
      const fields = Object.entries(error.fieldErrors);
      toast.error(
        fields.length > 0
          ? fields.map(([field, message]) => `${field}: ${message}`).join(' · ')
          : error.message,
      );
    },
  });

  if (!draft || (!isNew && post.isLoading)) {
    return <InlineLoader label="Yazı yüklənir…" />;
  }

  const translation = draft.translations[language] ?? EMPTY_TRANSLATION;
  const activeLanguages = languages.data ?? [{ code: 'az' } as Language];
  const scheduled =
    draft.status === 'ACTIVE' &&
    publishedAtInput !== '' &&
    new Date(publishedAtInput).getTime() > Date.now();

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link
            to="/admin/posts"
            className="inline-flex items-center gap-1.5 hover:text-[var(--color-heading)]"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            Bloq
          </Link>
        }
        title={isNew ? 'Yeni yazı' : (draft.translations['az']?.title || 'Yazı')}
        description={!isNew ? `/bloq/${draft.slug}` : undefined}
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

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <Card title="Məzmun" description={`${language.toUpperCase()} dili`}>
            <div className="space-y-4">
              <Field label="Başlıq" htmlFor="title" required={language === 'az'}>
                <Input
                  id="title"
                  value={translation.title}
                  onChange={(event) => patchTranslation(language, { title: event.target.value })}
                />
              </Field>

              <Field
                label="Qısa təsvir"
                htmlFor="shortDescription"
                hint="Siyahı kartında və paylaşımda görünür"
              >
                <Textarea
                  id="shortDescription"
                  rows={2}
                  value={translation.shortDescription}
                  onChange={(event) =>
                    patchTranslation(language, { shortDescription: event.target.value })
                  }
                />
              </Field>

              <Field
                label="Mətn"
                htmlFor="content"
                hint="HTML dəstəklənir və serverdə təhlükəsizlik üçün təmizlənir"
              >
                <Textarea
                  id="content"
                  rows={18}
                  className="font-mono text-[0.8125rem] leading-relaxed"
                  value={translation.content}
                  onChange={(event) => patchTranslation(language, { content: event.target.value })}
                  placeholder="<p>Məqalənin mətni…</p>"
                />
              </Field>
            </div>
          </Card>

          <Card title="SEO" description={`${language.toUpperCase()} dili üçün`}>
            <div className="space-y-4">
              <Field
                label="SEO başlığı"
                htmlFor="seoTitle"
                hint={`${translation.seoTitle.length}/60 simvol`}
              >
                <Input
                  id="seoTitle"
                  value={translation.seoTitle}
                  onChange={(event) => patchTranslation(language, { seoTitle: event.target.value })}
                  placeholder={translation.title}
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
                  value={translation.seoDescription}
                  onChange={(event) =>
                    patchTranslation(language, { seoDescription: event.target.value })
                  }
                  placeholder={translation.shortDescription}
                />
              </Field>

              <div className="rounded-[var(--radius-button)] border border-[var(--color-border)] bg-[var(--color-muted)] p-4">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
                  Axtarış nəticəsi
                </p>
                <p className="text-xs text-[var(--color-success)]">
                  /bloq/{draft.slug || 'yazi-adi'}
                </p>
                <p className="mt-0.5 text-base text-[#1a0dab]">
                  {translation.seoTitle || translation.title || 'Başlıq'}
                </p>
                <p className="mt-0.5 line-clamp-2 text-xs text-[var(--color-body)]">
                  {translation.seoDescription || translation.shortDescription || 'Təsvir yoxdur.'}
                </p>
              </div>
            </div>
          </Card>
        </div>

        {/* ---------------- side ---------------- */}
        <div className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          <Card title="Dərc">
            <Field label="Status" htmlFor="status">
              <Select
                id="status"
                value={draft.status}
                onChange={(event) => patch({ status: event.target.value as Status })}
              >
                <option value="DRAFT">Qaralama — saytda görünmür</option>
                <option value="ACTIVE">Dərc olunub</option>
                <option value="ARCHIVED">Arxiv</option>
              </Select>
            </Field>

            <Field
              label="Dərc tarixi"
              htmlFor="publishedAt"
              className="mt-4"
              hint="Gələcək tarix təyin edilsə yazı həmin vaxta planlaşdırılır"
            >
              <Input
                id="publishedAt"
                type="datetime-local"
                value={publishedAtInput}
                onChange={(event) => {
                  setPublishedAtInput(event.target.value);
                  setDirty(true);
                }}
              />
            </Field>

            {scheduled && (
              <p className="mt-3 flex items-start gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)]/8 px-3 py-2.5 text-xs text-[var(--color-primary)]">
                <CalendarClock size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
                Yazı təyin edilmiş vaxtda avtomatik görünəcək — əlavə əməliyyat lazım deyil.
              </p>
            )}

            <div className="mt-4">
              <Toggle
                label="Seçilmiş"
                hint="Bloq səhifəsində əsas kart kimi göstərilir"
                checked={draft.featured}
                onChange={(checked) => patch({ featured: checked })}
              />
            </div>

            {dirty && (
              <p className="mt-4 rounded-[var(--radius-button)] bg-[var(--color-warning)]/10 px-3 py-2 text-xs text-[var(--color-warning)]">
                Yadda saxlanmamış dəyişikliklər var.
              </p>
            )}
          </Card>

          <Card title="Örtük şəkli">
            {draft.coverUrl ? (
              <div className="relative overflow-hidden rounded-[var(--radius-button)] border border-[var(--color-border)]">
                <img src={draft.coverUrl} alt="" className="h-32 w-full object-cover" />
                <div className="absolute inset-x-0 bottom-0 flex gap-1 bg-black/60 p-1.5">
                  <button
                    type="button"
                    onClick={() => setCoverPickerOpen(true)}
                    className="flex-1 rounded py-1 text-xs font-medium text-white hover:bg-white/20"
                  >
                    Dəyiş
                  </button>
                  <button
                    type="button"
                    onClick={() => patch({ coverMediaId: null, coverUrl: null })}
                    className="flex-1 rounded py-1 text-xs font-medium text-white hover:bg-white/20"
                  >
                    Sil
                  </button>
                </div>
              </div>
            ) : (
              <Button
                variant="secondary"
                size="sm"
                className="w-full"
                icon={<ImagePlus size={14} aria-hidden="true" />}
                onClick={() => setCoverPickerOpen(true)}
              >
                Şəkil seç
              </Button>
            )}
          </Card>

          <Card title="Təsnifat">
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

            <Field label="Slug" htmlFor="slug" className="mt-4" hint="Boş qoyulsa başlıqdan yaradılır">
              <Input
                id="slug"
                value={draft.slug}
                onChange={(event) => patch({ slug: event.target.value })}
                placeholder="avtomatik"
              />
            </Field>

            {(tags.data ?? []).length > 0 && (
              <div className="mt-4">
                <p className="mb-2 text-sm font-medium text-[var(--color-heading)]">Teqlər</p>
                <div className="flex flex-wrap gap-1.5">
                  {tags.data!.map((tag) => {
                    const selected = draft.tagIds.includes(tag.id);
                    return (
                      <button
                        key={tag.id}
                        type="button"
                        onClick={() =>
                          patch({
                            tagIds: selected
                              ? draft.tagIds.filter((value) => value !== tag.id)
                              : [...draft.tagIds, tag.id],
                          })
                        }
                        className={clsx(
                          'rounded-full border px-3 py-1 text-xs font-medium transition-colors',
                          selected
                            ? 'border-[var(--color-primary)] bg-[var(--color-primary)]/8 text-[var(--color-primary)]'
                            : 'border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
                        )}
                      >
                        {tag.name}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            <Field
              label="Oxunuş müddəti (dəq)"
              htmlFor="readingMinutes"
              className="mt-4"
              hint="Boş qoyulsa mətndən avtomatik hesablanır"
            >
              <Input
                id="readingMinutes"
                type="number"
                min="1"
                value={draft.readingMinutes ?? ''}
                onChange={(event) =>
                  patch({
                    readingMinutes: event.target.value ? Number(event.target.value) : null,
                  })
                }
                placeholder="avtomatik"
              />
            </Field>
          </Card>

          {!isNew && (
            <Card title="Saytda">
              <a
                href={`/bloq/${draft.slug}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                Yazını saytda aç
              </a>
              <p className="mt-2 text-xs text-[var(--color-subtle)]">
                {draft.viewCount} baxış
              </p>
            </Card>
          )}
        </div>
      </div>

      <MediaPicker
        open={coverPickerOpen}
        type="IMAGE"
        onClose={() => setCoverPickerOpen(false)}
        onSelect={(picked) => {
          const media = picked[0];
          if (media) {
            patch({ coverMediaId: media.id, coverUrl: media.publicUrl });
          }
        }}
      />
    </>
  );
}
