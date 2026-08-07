import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CalendarClock, Newspaper, Plus, Star, Trash2 } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, getPage, patchData, postData } from '@/lib/api';
import { formatDateTime, formatNumber } from '@/lib/format';

type Status = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

interface PostRow {
  id: number;
  slug: string;
  title: string;
  coverUrl: string | null;
  categoryName: string | null;
  status: Status;
  featured: boolean;
  scheduled: boolean;
  viewCount: number;
  publishedAt: string | null;
  updatedAt: string | null;
}

interface BlogCategory {
  id: number;
  slug: string;
  name: string;
  color: string | null;
  postCount: number;
}

const STATUS_LABEL: Record<Status, string> = {
  ACTIVE: 'Dərc olunub',
  DRAFT: 'Qaralama',
  ARCHIVED: 'Arxiv',
};

/**
 * Blog post list.
 *
 * "Scheduled" is shown as its own state rather than folded into ACTIVE — a post set to
 * appear next Monday is neither live nor a draft, and an editor needs to see which.
 */
export function PostsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [managingCategories, setManagingCategories] = useState(false);

  const posts = useQuery({
    queryKey: ['admin', 'posts', { search, status, categoryId, page }],
    queryFn: () =>
      getPage<PostRow>('/admin/posts', {
        search: search || undefined,
        status: status || undefined,
        categoryId: categoryId || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
  });

  const categories = useQuery({
    queryKey: ['admin', 'post-categories'],
    queryFn: () => getData<BlogCategory[]>('/admin/post-categories'),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'posts'] });
    queryClient.invalidateQueries({ queryKey: ['posts'] });
  };

  const toggleFeatured = useMutation({
    mutationFn: ({ id, featured }: { id: number; featured: boolean }) =>
      patchData(`/admin/posts/${id}/featured`, { featured }),
    onSuccess: invalidate,
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/posts/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Yazı silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const rows = posts.data;

  return (
    <>
      <PageHeader
        title="Bloq"
        description="Məqalələr və xəbərlər. Gələcək tarix təyin edilsə yazı avtomatik dərc olunur."
        actions={
          <>
            <Button variant="secondary" onClick={() => setManagingCategories((open) => !open)}>
              Kateqoriyalar
            </Button>
            <Link to="/admin/posts/new">
              <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni yazı</Button>
            </Link>
          </>
        }
      />

      {managingCategories && (
        <CategoryManager
          categories={categories.data ?? []}
          onClose={() => setManagingCategories(false)}
          onChanged={() => queryClient.invalidateQueries({ queryKey: ['admin', 'post-categories'] })}
        />
      )}

      <Card bodyClassName="p-0">
        <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Başlıq"
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
            <option value="ACTIVE">Dərc olunub</option>
            <option value="DRAFT">Qaralama</option>
            <option value="ARCHIVED">Arxiv</option>
          </Select>
          <Select
            value={categoryId}
            onChange={(event) => {
              setCategoryId(event.target.value);
              setPage(0);
            }}
            className="max-w-[200px]"
          >
            <option value="">Bütün kateqoriyalar</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </div>

        {posts.isLoading ? (
          <TableSkeleton rows={6} columns={5} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState
            icon={<Newspaper size={36} aria-hidden="true" />}
            title="Yazı yoxdur"
            description="İlk bloq yazınızı yaradın."
            action={
              <Link to="/admin/posts/new">
                <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni yazı</Button>
              </Link>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                  <th className="px-5 py-3 text-start font-semibold">Yazı</th>
                  <th className="px-5 py-3 text-start font-semibold">Kateqoriya</th>
                  <th className="px-5 py-3 text-start font-semibold">Status</th>
                  <th className="px-5 py-3 text-end font-semibold">Baxış</th>
                  <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {rows.content.map((post) => (
                  <tr key={post.id} className="transition-colors hover:bg-[var(--color-muted)]">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-3">
                        <div className="h-11 w-16 shrink-0 overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
                          {post.coverUrl && (
                            <img
                              src={post.coverUrl}
                              alt=""
                              loading="lazy"
                              className="h-full w-full object-cover"
                            />
                          )}
                        </div>
                        <div className="min-w-0">
                          <Link
                            to={`/admin/posts/${post.id}`}
                            className="block truncate font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                          >
                            {post.title}
                          </Link>
                          <p className="truncate font-mono text-xs text-[var(--color-subtle)]">
                            /{post.slug}
                          </p>
                        </div>
                      </div>
                    </td>

                    <td className="px-5 py-3 text-[var(--color-body)]">
                      {post.categoryName ?? <span className="text-[var(--color-subtle)]">—</span>}
                    </td>

                    <td className="px-5 py-3">
                      <StateBadge post={post} />
                      {post.publishedAt && (
                        <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                          {formatDateTime(post.publishedAt)}
                        </p>
                      )}
                    </td>

                    <td className="px-5 py-3 text-end tabular text-[var(--color-body)]">
                      {formatNumber(post.viewCount)}
                    </td>

                    <td className="px-5 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          title={post.featured ? 'Seçilmişdən çıxar' : 'Seçilmişə əlavə et'}
                          onClick={() =>
                            toggleFeatured.mutate({ id: post.id, featured: !post.featured })
                          }
                          icon={
                            <Star
                              size={15}
                              className={clsx(
                                post.featured &&
                                  'fill-[var(--color-warning)] text-[var(--color-warning)]',
                              )}
                              aria-hidden="true"
                            />
                          }
                        />
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Sil"
                          className="text-[var(--color-danger)]"
                          onClick={() => {
                            if (window.confirm(`"${post.title}" silinsin?`)) {
                              remove.mutate(post.id);
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
              {formatNumber(rows.totalElements)} yazı
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
    </>
  );
}

function StateBadge({ post }: { post: PostRow }) {
  let tone: BadgeTone = 'neutral';
  let label: string = STATUS_LABEL[post.status];

  if (post.scheduled) {
    return (
      <Badge tone="info">
        <CalendarClock size={11} aria-hidden="true" />
        Planlaşdırılıb
      </Badge>
    );
  }
  if (post.status === 'ACTIVE') {
    tone = 'success';
  } else if (post.status === 'DRAFT') {
    tone = 'warning';
  }

  return <Badge tone={tone}>{label}</Badge>;
}

/** Inline category management — categories are few and rarely edited. */
function CategoryManager({
  categories,
  onClose,
  onChanged,
}: {
  categories: BlogCategory[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [color, setColor] = useState('#4F46E5');

  const create = useMutation({
    mutationFn: () =>
      postData('/admin/post-categories', {
        color,
        status: 'ACTIVE',
        names: { az: name.trim() },
      }),
    onSuccess: () => {
      setName('');
      onChanged();
      toast.success('Kateqoriya yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/post-categories/${id}`),
    onSuccess: () => {
      onChanged();
      toast.success('Kateqoriya silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <Card
      title="Bloq kateqoriyaları"
      className="mb-6"
      actions={
        <Button variant="ghost" size="sm" onClick={onClose}>
          Bağla
        </Button>
      }
    >
      {categories.length > 0 && (
        <ul className="mb-5 flex flex-wrap gap-2">
          {categories.map((category) => (
            <li
              key={category.id}
              className="flex items-center gap-2 rounded-full border border-[var(--color-border)] py-1 pe-1 ps-3 text-sm"
            >
              <span
                className="h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: category.color ?? 'var(--color-primary)' }}
                aria-hidden="true"
              />
              {category.name}
              <span className="text-xs text-[var(--color-subtle)] tabular">
                {category.postCount}
              </span>
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(`"${category.name}" silinsin?`)) {
                    remove.mutate(category.id);
                  }
                }}
                aria-label={`${category.name} sil`}
                className="rounded-full p-1 text-[var(--color-subtle)] hover:bg-[var(--color-muted)] hover:text-[var(--color-danger)]"
              >
                <Trash2 size={12} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <Field label="Yeni kateqoriya" htmlFor="categoryName" className="min-w-[200px] flex-1">
          <Input
            id="categoryName"
            value={name}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && name.trim()) {
                event.preventDefault();
                create.mutate();
              }
            }}
            placeholder="Məsləhətlər"
          />
        </Field>

        <Field label="Rəng" htmlFor="categoryColor">
          <input
            id="categoryColor"
            type="color"
            value={color}
            onChange={(event) => setColor(event.target.value)}
            className="h-10 w-14 cursor-pointer rounded-[var(--radius-input)] border border-[var(--color-border)]"
          />
        </Field>

        <Button loading={create.isPending} disabled={!name.trim()} onClick={() => create.mutate()}>
          Əlavə et
        </Button>
      </div>

      <p className="mt-3 text-xs text-[var(--color-subtle)]">
        Yazıları olan kateqoriya silinmir — əvvəlcə yazıları başqa kateqoriyaya köçürün.
      </p>
    </Card>
  );
}
