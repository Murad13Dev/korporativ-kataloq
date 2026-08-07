import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText, Lock, Plus, Trash2 } from 'lucide-react';
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
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData } from '@/lib/api';
import { formatDate } from '@/lib/format';
import type { Language } from '@/types/api';

type PageType = 'STANDARD' | 'HOME' | 'CONTACT' | 'ABOUT' | 'CATALOG' | 'BLOG' | 'LEGAL' | 'LANDING';

interface LanguageState {
  languageCode: string;
  title: string | null;
  slug: string | null;
  published: boolean;
  hasDraft: boolean;
  publishedAt: string | null;
}

interface PageRow {
  id: number;
  code: string;
  pageType: PageType;
  system: boolean;
  layout: string;
  sortOrder: number;
  languages: LanguageState[];
  updatedAt: string | null;
}

const PAGE_TYPES: { value: PageType; label: string }[] = [
  { value: 'STANDARD', label: 'Adi səhifə' },
  { value: 'LANDING', label: 'Landing' },
  { value: 'ABOUT', label: 'Haqqımızda' },
  { value: 'CONTACT', label: 'Əlaqə' },
  { value: 'LEGAL', label: 'Hüquqi' },
];

const PAGE_TYPE_LABEL: Record<PageType, string> = {
  STANDARD: 'Adi səhifə',
  HOME: 'Ana səhifə',
  CONTACT: 'Əlaqə',
  ABOUT: 'Haqqımızda',
  CATALOG: 'Kataloq',
  BLOG: 'Bloq',
  LEGAL: 'Hüquqi',
  LANDING: 'Landing',
};

/**
 * Page list.
 *
 * The per-language column is the point of this screen: a page is not simply "published",
 * it is published in some languages and still a draft in others, and an operator needs to
 * see that at a glance rather than by opening each one.
 */
export function PagesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [creating, setCreating] = useState(false);

  const pages = useQuery({
    queryKey: ['admin', 'pages'],
    queryFn: () => getData<PageRow[]>('/admin/pages'),
  });

  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => getData<Language[]>('/public/languages'),
    staleTime: 30 * 60 * 1000,
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/pages/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'pages'] });
      toast.success('Səhifə silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <>
      <PageHeader
        title="Səhifələr"
        description="Hazır bölmələrdən qurulan səhifələr. Dəyişikliklər dərc olunana qədər saytda görünmür."
        actions={
          <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setCreating(true)}>
            Yeni səhifə
          </Button>
        }
      />

      {creating && (
        <CreatePageForm
          languages={languages.data ?? []}
          onCancel={() => setCreating(false)}
          onCreated={() => {
            setCreating(false);
            queryClient.invalidateQueries({ queryKey: ['admin', 'pages'] });
          }}
        />
      )}

      <Card bodyClassName="p-0">
        {pages.isLoading ? (
          <InlineLoader />
        ) : !pages.data || pages.data.length === 0 ? (
          <EmptyState
            icon={<FileText size={36} aria-hidden="true" />}
            title="Səhifə yoxdur"
            description="İlk səhifənizi yaradın və hazır bölmələrdən quraşdırın."
            action={
              <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setCreating(true)}>
                Yeni səhifə
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                  <th className="px-5 py-3 text-start font-semibold">Səhifə</th>
                  <th className="px-5 py-3 text-start font-semibold">Tip</th>
                  <th className="px-5 py-3 text-start font-semibold">Dillər</th>
                  <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {pages.data.map((page) => (
                  <tr key={page.id} className="transition-colors hover:bg-[var(--color-muted)]">
                    <td className="px-5 py-3.5">
                      <Link
                        to={`/admin/pages/${page.id}`}
                        className="font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                      >
                        {page.languages[0]?.title ?? page.code}
                      </Link>
                      <p className="flex items-center gap-1.5 font-mono text-xs text-[var(--color-subtle)]">
                        {page.code}
                        {page.system && (
                          <span
                            className="inline-flex items-center gap-0.5"
                            title="Sistem səhifəsi — silinə bilməz"
                          >
                            <Lock size={10} aria-hidden="true" />
                          </span>
                        )}
                      </p>
                    </td>

                    <td className="px-5 py-3.5 text-[var(--color-body)]">
                      {PAGE_TYPE_LABEL[page.pageType]}
                    </td>

                    <td className="px-5 py-3.5">
                      <div className="flex flex-wrap gap-1.5">
                        {page.languages.length === 0 ? (
                          <span className="text-xs text-[var(--color-subtle)]">Versiya yoxdur</span>
                        ) : (
                          page.languages.map((state) => (
                            <span
                              key={state.languageCode}
                              title={
                                state.published
                                  ? `Dərc edilib: ${formatDate(state.publishedAt)}`
                                  : 'Dərc edilməyib'
                              }
                            >
                              <Badge
                                tone={
                                  state.published && state.hasDraft
                                    ? 'warning'
                                    : state.published
                                      ? 'success'
                                      : 'neutral'
                                }
                              >
                                {state.languageCode.toUpperCase()}
                                {state.hasDraft && ' •'}
                              </Badge>
                            </span>
                          ))
                        )}
                      </div>
                    </td>

                    <td className="px-5 py-3.5">
                      <div className="flex items-center justify-end gap-1">
                        <Link to={`/admin/pages/${page.id}`}>
                          <Button variant="secondary" size="sm">
                            Redaktə
                          </Button>
                        </Link>
                        {!page.system && (
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Sil"
                            className="text-[var(--color-danger)]"
                            onClick={() => {
                              if (window.confirm(`"${page.code}" səhifəsi silinsin?`)) {
                                remove.mutate(page.id);
                              }
                            }}
                            icon={<Trash2 size={15} aria-hidden="true" />}
                          />
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <p className="border-t border-[var(--color-border)] px-5 py-3 text-xs text-[var(--color-subtle)]">
          <strong className="text-[var(--color-heading)]">•</strong> işarəsi dərc edilməmiş
          dəyişikliklərin olduğunu bildirir.
        </p>
      </Card>
    </>
  );
}

function CreatePageForm({
  languages,
  onCancel,
  onCreated,
}: {
  languages: Language[];
  onCancel: () => void;
  onCreated: () => void;
}) {
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [code, setCode] = useState('');
  const [pageType, setPageType] = useState<PageType>('STANDARD');
  const [languageCode, setLanguageCode] = useState(languages[0]?.code ?? 'az');

  const create = useMutation({
    mutationFn: () =>
      postData('/admin/pages', {
        code: code || title.toUpperCase().replace(/[^A-Z0-9]+/g, '_').slice(0, 64),
        pageType,
        languageCode,
        title,
      }),
    onSuccess: () => {
      toast.success('Səhifə yaradıldı');
      onCreated();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <Card
      title="Yeni səhifə"
      description="Səhifə ilə birlikdə ilk draft versiya yaradılır"
      className="mb-6"
      actions={
        <Button variant="ghost" size="sm" onClick={onCancel}>
          Bağla
        </Button>
      }
    >
      <div className="grid gap-4 sm:grid-cols-4">
        <Field label="Başlıq" htmlFor="pageTitle" required className="sm:col-span-2">
          <Input
            id="pageTitle"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="Xidmətlərimiz"
          />
        </Field>

        <Field label="Kod" htmlFor="pageCode" hint="Boş qoyulsa başlıqdan yaradılır">
          <Input
            id="pageCode"
            value={code}
            onChange={(event) =>
              setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))
            }
            className="font-mono"
            placeholder="SERVICES"
          />
        </Field>

        <Field label="Tip" htmlFor="pageTypeSelect">
          <Select
            id="pageTypeSelect"
            value={pageType}
            onChange={(event) => setPageType(event.target.value as PageType)}
          >
            {PAGE_TYPES.map((entry) => (
              <option key={entry.value} value={entry.value}>
                {entry.label}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Dil" htmlFor="pageLanguage">
          <Select
            id="pageLanguage"
            value={languageCode}
            onChange={(event) => setLanguageCode(event.target.value)}
          >
            {languages.map((language) => (
              <option key={language.code} value={language.code}>
                {language.nativeName ?? language.name}
              </option>
            ))}
          </Select>
        </Field>

        <div className="flex items-end sm:col-span-3">
          <Button loading={create.isPending} disabled={!title.trim()} onClick={() => create.mutate()}>
            Yarat və redaktə et
          </Button>
        </div>
      </div>
    </Card>
  );
}
