import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { SectionList } from '@/components/sections/SectionRenderer';
import { PageLoader } from '@/components/ui/PageLoader';
import NotFoundPage from './NotFoundPage';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { ApiError, getData } from '@/lib/api';
import { setPageMeta } from '@/lib/theme';
import type { PublicPage } from '@/components/sections/types';

/**
 * Renders any CMS page from its sections.
 *
 * This is the catch-all route, so a slug that is not a published page must fall through
 * to a real 404 rather than an error screen — the URL may simply be a typo.
 */
export default function DynamicPage({ code }: { code?: string }) {
  const { slug } = useParams<{ slug: string }>();
  const { config } = useSiteConfig();

  const query = useQuery({
    queryKey: ['page', code ?? slug],
    queryFn: () =>
      code
        ? getData<PublicPage>(`/public/pages/by-code/${code}`)
        : getData<PublicPage>(`/public/pages/${slug}`),
    enabled: Boolean(code || slug),
    retry: (failureCount, error) =>
      // A 404 here is a legitimate answer, not a transient failure.
      !(error instanceof ApiError && error.status === 404) && failureCount < 2,
  });

  const page = query.data;

  useEffect(() => {
    if (!page) {
      return;
    }
    return setPageMeta(config, {
      title: page.seoTitle ?? page.title,
      description: page.seoDescription,
      canonicalUrl: page.canonicalUrl,
      ogImage: page.ogUrl ?? page.coverUrl,
      allowIndexing: page.allowIndexing,
    });
  }, [config, page]);

  if (query.isLoading) {
    return <PageLoader />;
  }

  if (query.isError || !page) {
    return <NotFoundPage />;
  }

  if (page.sections.length === 0) {
    return (
      <div className="container-site py-20 text-center">
        <h1 className="text-3xl font-bold tracking-tight">{page.title}</h1>
        {page.shortDescription && (
          <p className="mx-auto mt-3 max-w-xl text-[var(--color-body)]">
            {page.shortDescription}
          </p>
        )}
      </div>
    );
  }

  return <SectionList sections={page.sections} pageCode={page.code} />;
}
