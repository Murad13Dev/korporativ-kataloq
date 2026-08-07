import type { ComponentType } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { SectionShell, isInverted, SectionButton } from './SectionShell';
import {
  CtaBannerSection,
  GallerySection,
  HeroSection,
  HtmlEmbedSection,
  PageHeaderSection,
  RichTextSection,
  SpacerSection,
  TeamSection,
  TextWithImageSection,
  TimelineSection,
} from './ContentSections';
import {
  DiscountedProductsSection,
  FeaturedCategoriesSection,
  FeaturedProductsSection,
  ProductListSection,
} from './CatalogSections';
import {
  BenefitsSection,
  FaqSection,
  PartnersSection,
  StatisticsSection,
  TestimonialsSection,
} from './SocialSections';
import { ContactFormSection, MapSection, NewsletterSection } from './FormSections';
import { getPage } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { configNumber, type PublicSection, type SectionProps, type SectionTypeName } from './types';

// ---------------------------------------------------------------------------
// LATEST_POSTS
//
// Lives here rather than in SocialSections because it is the only section that
// touches the blog API.
// ---------------------------------------------------------------------------

interface PostCard {
  id: number;
  slug: string;
  title: string;
  shortDescription: string | null;
  coverUrl: string | null;
  categoryName: string | null;
  publishedAt: string | null;
  readingMinutes: number | null;
}

function LatestPostsSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const limit = configNumber(section, 'limit', 3, 12);

  const query = useQuery({
    queryKey: ['sections', 'posts', limit],
    queryFn: () =>
      getPage<PostCard>('/public/posts', { size: limit, page: 0 })
        .then((page) => page.content)
        // The blog module is optional; a missing endpoint hides the section rather
        // than breaking the page around it.
        .catch(() => [] as PostCard[]),
    staleTime: 10 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  const isList = section.variant === 'LIST';

  return (
    <SectionShell section={section}>
      <div className={clsx(isList ? 'space-y-4' : 'grid gap-5 md:grid-cols-3')}>
        {query.isLoading
          ? Array.from({ length: limit }).map((_, index) => (
              <div key={index} className="skeleton h-56 rounded-[var(--radius-card)]" />
            ))
          : query.data!.map((post) => (
              <article
                key={post.id}
                className={clsx('card card-elevated overflow-hidden', isList && 'flex gap-4')}
              >
                <Link
                  to={`/bloq/${post.slug}`}
                  className={clsx(isList ? 'flex flex-1 gap-4' : 'block')}
                >
                  <div
                    className={clsx(
                      'bg-[var(--color-muted)]',
                      isList ? 'h-28 w-40 shrink-0' : 'aspect-[16/10]',
                    )}
                  >
                    {post.coverUrl && (
                      <img
                        src={post.coverUrl}
                        alt=""
                        loading="lazy"
                        className="h-full w-full object-cover"
                      />
                    )}
                  </div>

                  <div className="flex-1 p-4">
                    {post.categoryName && (
                      <span className="text-xs font-semibold uppercase tracking-wide text-[var(--color-primary)]">
                        {post.categoryName}
                      </span>
                    )}
                    <h3 className="mt-1 line-clamp-2 text-base font-semibold text-[var(--color-heading)]">
                      {post.title}
                    </h3>
                    {post.shortDescription && (
                      <p className="mt-1.5 line-clamp-2 text-sm text-[var(--color-body)]">
                        {post.shortDescription}
                      </p>
                    )}
                    <p className="mt-2 text-xs text-[var(--color-subtle)]">
                      {formatDate(post.publishedAt)}
                      {post.readingMinutes && ` · ${post.readingMinutes} dəq oxunuş`}
                    </p>
                  </div>
                </Link>
              </article>
            ))}
      </div>

      {section.buttonText && (
        <div className="mt-8 text-center">
          <SectionButton
            text={section.buttonText}
            url={section.buttonUrl}
            variant="secondary"
            inverted={inverted}
          />
        </div>
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * The section registry — the client half of the contract the backend `SectionType` enum
 * defines. Every type the server can emit has exactly one component here.
 *
 * Keeping this a lookup rather than a switch means an unknown type (a newer backend
 * talking to an older bundle) is a missing entry we can handle deliberately, not a
 * crashed render.
 */
const REGISTRY: Partial<Record<SectionTypeName, ComponentType<SectionProps>>> = {
  HERO: HeroSection,
  PAGE_HEADER: PageHeaderSection,
  RICH_TEXT: RichTextSection,
  TEXT_WITH_IMAGE: TextWithImageSection,

  FEATURED_CATEGORIES: FeaturedCategoriesSection,
  FEATURED_PRODUCTS: FeaturedProductsSection,
  DISCOUNTED_PRODUCTS: DiscountedProductsSection,
  PRODUCT_LIST: ProductListSection,

  BENEFITS: BenefitsSection,
  STATISTICS: StatisticsSection,
  TESTIMONIALS: TestimonialsSection,
  PARTNERS: PartnersSection,
  LATEST_POSTS: LatestPostsSection,
  FAQ: FaqSection,

  GALLERY: GallerySection,
  TIMELINE: TimelineSection,
  TEAM: TeamSection,

  CTA_BANNER: CtaBannerSection,
  CONTACT_FORM: ContactFormSection,
  NEWSLETTER: NewsletterSection,
  MAP: MapSection,

  SPACER: SpacerSection,
  HTML_EMBED: HtmlEmbedSection,
};

/**
 * Renders one section.
 *
 * An unregistered type renders nothing in production. Showing a shopper a placeholder
 * for a component we cannot draw is worse than a slightly shorter page; in development
 * it is surfaced so the gap gets noticed.
 */
export function SectionRenderer({ section, pageCode }: SectionProps) {
  const Component = REGISTRY[section.type];

  if (!Component) {
    if (import.meta.env.DEV) {
      return (
        <div className="container-site py-6">
          <p className="rounded-[var(--radius-card)] border border-dashed border-[var(--color-warning)] bg-[var(--color-warning)]/8 px-4 py-3 text-sm text-[var(--color-warning)]">
            Naməlum bölmə tipi: <code className="font-mono">{section.type}</code> — bu mesaj
            yalnız development rejimində görünür.
          </p>
        </div>
      );
    }
    return null;
  }

  return <Component section={section} pageCode={pageCode} />;
}

/** Renders a whole page's sections in order. */
export function SectionList({
  sections,
  pageCode,
}: {
  sections: PublicSection[];
  pageCode?: string;
}) {
  return (
    <>
      {sections.map((section) => (
        <SectionRenderer key={section.id} section={section} pageCode={pageCode} />
      ))}
    </>
  );
}

export { REGISTRY as sectionRegistry };
