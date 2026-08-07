import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Clock, Eye, Tag as TagIcon, User } from 'lucide-react';
import { PageLoader } from '@/components/ui/PageLoader';
import NotFoundPage from './NotFoundPage';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { ApiError, getData } from '@/lib/api';
import { formatDate, formatNumber } from '@/lib/format';
import { setJsonLd, setPageMeta } from '@/lib/theme';

interface TagDto {
  id: number;
  slug: string;
  name: string;
}

interface PostDetail {
  id: number;
  slug: string;
  title: string;
  shortDescription: string | null;
  content: string | null;
  coverUrl: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  categorySlug: string | null;
  categoryName: string | null;
  authorName: string | null;
  readingMinutes: number | null;
  viewCount: number;
  tags: TagDto[];
  publishedAt: string | null;
}

interface PostCard {
  id: number;
  slug: string;
  title: string;
  shortDescription: string | null;
  coverUrl: string | null;
  categoryName: string | null;
  readingMinutes: number | null;
  publishedAt: string | null;
}

/**
 * Article page.
 *
 * The body is capped at a readable measure (~68 characters) rather than filling the
 * container: long lines are the single biggest cause of readers losing their place.
 */
export default function BlogDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { config } = useSiteConfig();

  const post = useQuery({
    queryKey: ['post', slug],
    queryFn: () => getData<PostDetail>(`/public/posts/${slug}`),
    enabled: Boolean(slug),
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.status === 404) && failureCount < 2,
  });

  const related = useQuery({
    queryKey: ['post', slug, 'related'],
    queryFn: () => getData<PostCard[]>(`/public/posts/${slug}/related`, { limit: 3 }),
    enabled: Boolean(slug) && post.isSuccess,
  });

  const data = post.data;

  useEffect(() => {
    if (!data) {
      return;
    }
    return setPageMeta(config, {
      title: data.seoTitle ?? data.title,
      description: data.seoDescription ?? data.shortDescription,
      ogImage: data.coverUrl,
    });
  }, [config, data]);

  useEffect(() => {
    if (!data) {
      return;
    }
    const url = window.location.href;
    return setJsonLd('blog-post', {
      '@context': 'https://schema.org',
      '@type': 'BlogPosting',
      headline: data.title,
      description: data.shortDescription ?? data.seoDescription ?? undefined,
      image: data.coverUrl ?? undefined,
      author: data.authorName ? { '@type': 'Person', name: data.authorName } : undefined,
      datePublished: data.publishedAt ?? undefined,
      mainEntityOfPage: url,
    });
  }, [data]);

  if (post.isLoading) {
    return <PageLoader />;
  }

  if (post.isError || !data) {
    return <NotFoundPage />;
  }

  return (
    <article className="pb-16">
      {/* ---------------- cover ---------------- */}
      {data.coverUrl && (
        <div className="h-64 w-full bg-[var(--color-muted)] lg:h-96">
          <img
            src={data.coverUrl}
            alt=""
            loading="eager"
            fetchPriority="high"
            className="h-full w-full object-cover"
          />
        </div>
      )}

      <div className="container-site">
        <div className="mx-auto max-w-3xl">
          {/* ---------------- header ---------------- */}
          <header className={data.coverUrl ? '-mt-16 lg:-mt-20' : 'pt-10'}>
            <div
              className={
                data.coverUrl
                  ? 'rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-card)] lg:p-10'
                  : undefined
              }
            >
              <Link
                to="/bloq"
                className="mb-5 inline-flex items-center gap-1.5 text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                <ArrowLeft size={15} aria-hidden="true" />
                Bloqa qayıt
              </Link>

              {data.categoryName && data.categorySlug && (
                <Link
                  to={`/bloq?category=${data.categorySlug}`}
                  className="mb-3 block text-sm font-semibold uppercase tracking-wide text-[var(--color-primary)]"
                >
                  {data.categoryName}
                </Link>
              )}

              <h1 className="text-3xl font-bold leading-tight tracking-tight lg:text-4xl">
                {data.title}
              </h1>

              {data.shortDescription && (
                <p className="mt-4 text-lg leading-relaxed text-[var(--color-body)]">
                  {data.shortDescription}
                </p>
              )}

              <div className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-[var(--color-border)] pt-5 text-sm text-[var(--color-subtle)]">
                {data.authorName && (
                  <span className="inline-flex items-center gap-1.5">
                    <User size={14} aria-hidden="true" />
                    {data.authorName}
                  </span>
                )}
                <time dateTime={data.publishedAt ?? undefined}>{formatDate(data.publishedAt)}</time>
                {data.readingMinutes && (
                  <span className="inline-flex items-center gap-1.5">
                    <Clock size={14} aria-hidden="true" />
                    {data.readingMinutes} dəq oxunuş
                  </span>
                )}
                {data.viewCount > 0 && (
                  <span className="inline-flex items-center gap-1.5">
                    <Eye size={14} aria-hidden="true" />
                    {formatNumber(data.viewCount)}
                  </span>
                )}
              </div>
            </div>
          </header>

          {/* ---------------- body ---------------- */}
          {data.content && (
            <div
              /*
                Sanitised server-side by the OWASP policy on write, so rendering as
                markup here is safe — nothing unsanitised reaches the database.
              */
              className="prose-content mt-10 text-[1.0625rem] leading-[1.8]"
              dangerouslySetInnerHTML={{ __html: data.content }}
            />
          )}

          {/* ---------------- tags ---------------- */}
          {data.tags.length > 0 && (
            <div className="mt-10 flex flex-wrap items-center gap-2 border-t border-[var(--color-border)] pt-6">
              <TagIcon size={15} className="text-[var(--color-subtle)]" aria-hidden="true" />
              {data.tags.map((tag) => (
                <Link
                  key={tag.id}
                  to={`/bloq?tag=${tag.slug}`}
                  className="rounded-full border border-[var(--color-border)] px-3 py-1 text-xs font-medium text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
                >
                  {tag.name}
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* ---------------- related ---------------- */}
        {related.data && related.data.length > 0 && (
          <section className="mt-16">
            <h2 className="mb-6 text-2xl font-bold tracking-tight">Növbəti oxu</h2>
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {related.data.map((entry) => (
                <article key={entry.id} className="card card-elevated group overflow-hidden">
                  <Link to={`/bloq/${entry.slug}`} className="flex h-full flex-col">
                    <div className="aspect-[16/10] bg-[var(--color-muted)]">
                      {entry.coverUrl && (
                        <img
                          src={entry.coverUrl}
                          alt=""
                          loading="lazy"
                          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                        />
                      )}
                    </div>
                    <div className="flex flex-1 flex-col p-5">
                      {entry.categoryName && (
                        <span className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-[var(--color-primary)]">
                          {entry.categoryName}
                        </span>
                      )}
                      <h3 className="line-clamp-2 font-semibold leading-snug transition-colors group-hover:text-[var(--color-primary)]">
                        {entry.title}
                      </h3>
                      <p className="mt-auto pt-3 text-xs text-[var(--color-subtle)]">
                        {formatDate(entry.publishedAt)}
                        {entry.readingMinutes && ` · ${entry.readingMinutes} dəq`}
                      </p>
                    </div>
                  </Link>
                </article>
              ))}
            </div>
          </section>
        )}
      </div>
    </article>
  );
}
