import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Clock, Newspaper, Search } from 'lucide-react';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { getData, getPage } from '@/lib/api';
import { formatDate, formatNumber } from '@/lib/format';
import { setPageTitle } from '@/lib/theme';

interface PostCard {
  id: number;
  slug: string;
  title: string;
  shortDescription: string | null;
  coverUrl: string | null;
  categorySlug: string | null;
  categoryName: string | null;
  categoryColor: string | null;
  featured: boolean;
  readingMinutes: number | null;
  publishedAt: string | null;
}

interface BlogCategory {
  id: number;
  slug: string;
  name: string;
  color: string | null;
  postCount: number;
}

const PAGE_SIZE = 12;

/**
 * Blog index.
 *
 * Filters live in the query string so a filtered view is shareable and the back button
 * behaves. The featured post gets a wide lead card only on the unfiltered first page —
 * once someone is browsing a category, the hierarchy would be noise.
 */
export default function BlogListPage() {
  const { config } = useSiteConfig();
  const [searchParams, setSearchParams] = useSearchParams();

  const category = searchParams.get('category') ?? '';
  const tag = searchParams.get('tag') ?? '';
  const search = searchParams.get('search') ?? '';
  const page = Number(searchParams.get('page') ?? '0');

  useEffect(() => setPageTitle(config, 'Bloq'), [config]);

  const posts = useQuery({
    queryKey: ['posts', { category, tag, search, page }],
    queryFn: () =>
      getPage<PostCard>('/public/posts', {
        category: category || undefined,
        tag: tag || undefined,
        search: search || undefined,
        page,
        size: PAGE_SIZE,
      }),
    placeholderData: (previous) => previous,
  });

  const categories = useQuery({
    queryKey: ['post-categories'],
    queryFn: () => getData<BlogCategory[]>('/public/post-categories'),
    staleTime: 15 * 60 * 1000,
  });

  const featured = useQuery({
    queryKey: ['posts', 'featured'],
    queryFn: () => getData<PostCard[]>('/public/posts/featured', { limit: 1 }),
    // Only relevant on the plain first page.
    enabled: !category && !tag && !search && page === 0,
    staleTime: 10 * 60 * 1000,
  });

  const updateFilter = (key: string, value: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    if (key !== 'page') {
      next.delete('page');
    }
    setSearchParams(next);
  };

  const lead = featured.data?.[0];
  const list = posts.data;
  // The lead article must not appear twice on the same screen.
  const items = (list?.content ?? []).filter((post) => post.id !== lead?.id);

  return (
    <div className="container-site py-8 lg:py-12">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight lg:text-4xl">Bloq</h1>
        <p className="mt-2 text-[var(--color-body)]">
          Xəbərlər, məqalələr və faydalı məsləhətlər.
        </p>
      </header>

      {/* ---------------- filters ---------------- */}
      <div className="mb-8 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1 sm:max-w-xs">
          <Search
            size={16}
            className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]"
            aria-hidden="true"
          />
          <label htmlFor="blog-search" className="sr-only">
            Axtarış
          </label>
          <input
            id="blog-search"
            type="search"
            defaultValue={search}
            placeholder="Məqalə axtar…"
            onChange={(event) => {
              const value = event.target.value;
              window.clearTimeout((window as any).__blogSearchTimer);
              (window as any).__blogSearchTimer = window.setTimeout(
                () => updateFilter('search', value || null),
                350,
              );
            }}
            className="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] py-2.5 pe-3 ps-9 text-sm outline-none transition-colors focus:border-[var(--color-primary)]"
          />
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => updateFilter('category', null)}
            className={clsx(
              'rounded-full px-4 py-2 text-sm font-medium transition-colors',
              !category
                ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                : 'border border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
            )}
          >
            Hamısı
          </button>
          {(categories.data ?? []).map((entry) => (
            <button
              key={entry.id}
              type="button"
              onClick={() => updateFilter('category', entry.slug)}
              className={clsx(
                'rounded-full px-4 py-2 text-sm font-medium transition-colors',
                category === entry.slug
                  ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                  : 'border border-[var(--color-border)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
              )}
            >
              {entry.name}
              <span className="ms-1.5 text-xs opacity-70 tabular">{entry.postCount}</span>
            </button>
          ))}
        </div>
      </div>

      {tag && (
        <p className="mb-6 text-sm text-[var(--color-body)]">
          Teq: <strong className="text-[var(--color-heading)]">{tag}</strong>
          <button
            type="button"
            onClick={() => updateFilter('tag', null)}
            className="ms-2 text-[var(--color-primary)] hover:underline"
          >
            təmizlə
          </button>
        </p>
      )}

      {/* ---------------- lead article ---------------- */}
      {lead && (
        <Link
          to={`/bloq/${lead.slug}`}
          className="card card-elevated group mb-8 grid gap-0 overflow-hidden md:grid-cols-2"
        >
          <div className="aspect-[16/10] bg-[var(--color-muted)] md:aspect-auto">
            {lead.coverUrl && (
              <img
                src={lead.coverUrl}
                alt=""
                loading="eager"
                className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
              />
            )}
          </div>
          <div className="flex flex-col justify-center p-6 lg:p-8">
            <span className="mb-2 inline-flex w-fit rounded-full bg-[var(--color-primary)]/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-[var(--color-primary)]">
              Seçilmiş məqalə
            </span>
            <h2 className="text-2xl font-bold leading-tight tracking-tight transition-colors group-hover:text-[var(--color-primary)] lg:text-3xl">
              {lead.title}
            </h2>
            {lead.shortDescription && (
              <p className="mt-3 line-clamp-3 text-[var(--color-body)]">{lead.shortDescription}</p>
            )}
            <PostMeta post={lead} className="mt-4" />
          </div>
        </Link>
      )}

      {/* ---------------- grid ---------------- */}
      {posts.isLoading ? (
        <div className="flex flex-wrap justify-center gap-6">
          {Array.from({ length: 6 }).map((_, index) => (
            <div
              key={index}
              className="skeleton h-72 w-full rounded-[var(--radius-card)] sm:w-[calc(50%-0.75rem)] lg:w-[calc(33.333%-1rem)]"
            />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center rounded-[var(--radius-card)] border border-dashed border-[var(--color-border)] px-6 py-20 text-center">
          <Newspaper size={38} className="mb-4 text-[var(--color-subtle)]" aria-hidden="true" />
          <h2 className="mb-2 text-lg font-semibold">Yazı tapılmadı</h2>
          <p className="max-w-sm text-sm text-[var(--color-body)]">
            {search || category || tag
              ? 'Axtarışınıza uyğun məqalə yoxdur. Filtrləri dəyişməyi sınayın.'
              : 'Bloqda hələ dərc olunmuş yazı yoxdur.'}
          </p>
        </div>
      ) : (
        // flex-wrap + justify-center rather than a plain grid: a post count that isn't a
        // multiple of the column count would otherwise strand the last row flush-start
        // with empty tracks beside it (2 posts on a 3-column grid, say).
        <div className="flex flex-wrap justify-center gap-6">
          {items.map((post) => (
            <article
              key={post.id}
              className="card card-elevated group w-full overflow-hidden sm:w-[calc(50%-0.75rem)] lg:w-[calc(33.333%-1rem)]"
            >
              <Link to={`/bloq/${post.slug}`} className="flex h-full flex-col">
                <div className="aspect-[16/10] bg-[var(--color-muted)]">
                  {post.coverUrl && (
                    <img
                      src={post.coverUrl}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                  )}
                </div>

                <div className="flex flex-1 flex-col p-5">
                  {post.categoryName && (
                    <span
                      className="mb-2 text-xs font-semibold uppercase tracking-wide"
                      style={{ color: post.categoryColor ?? 'var(--color-primary)' }}
                    >
                      {post.categoryName}
                    </span>
                  )}

                  <h2 className="line-clamp-2 text-lg font-semibold leading-snug transition-colors group-hover:text-[var(--color-primary)]">
                    {post.title}
                  </h2>

                  {post.shortDescription && (
                    <p className="mt-2 line-clamp-3 text-sm text-[var(--color-body)]">
                      {post.shortDescription}
                    </p>
                  )}

                  <PostMeta post={post} className="mt-auto pt-4" />
                </div>
              </Link>
            </article>
          ))}
        </div>
      )}

      {/* ---------------- pagination ---------------- */}
      {list && list.totalPages > 1 && (
        <nav className="mt-10 flex items-center justify-center gap-2" aria-label="Səhifələr">
          <button
            type="button"
            disabled={list.first}
            onClick={() => updateFilter('page', String(page - 1))}
            className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2 text-sm disabled:opacity-40"
          >
            Əvvəlki
          </button>
          <span className="px-3 text-sm text-[var(--color-subtle)] tabular">
            {list.page + 1} / {list.totalPages}
          </span>
          <button
            type="button"
            disabled={list.last}
            onClick={() => updateFilter('page', String(page + 1))}
            className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-4 py-2 text-sm disabled:opacity-40"
          >
            Növbəti
          </button>
        </nav>
      )}

      {list && list.totalElements > 0 && (
        <p className="mt-4 text-center text-xs text-[var(--color-subtle)]">
          {formatNumber(list.totalElements)} yazı
        </p>
      )}
    </div>
  );
}

function PostMeta({ post, className }: { post: PostCard; className?: string }) {
  return (
    <p
      className={clsx(
        'flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-subtle)]',
        className,
      )}
    >
      <time dateTime={post.publishedAt ?? undefined}>{formatDate(post.publishedAt)}</time>
      {post.readingMinutes && (
        <span className="inline-flex items-center gap-1">
          <Clock size={12} aria-hidden="true" />
          {post.readingMinutes} dəq oxunuş
        </span>
      )}
    </p>
  );
}
