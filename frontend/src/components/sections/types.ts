/**
 * Wire types for the public page renderer.
 *
 * These mirror `PageDtos.PublicPage` on the server. Hidden sections are already filtered
 * out server-side, so anything that arrives here is meant to be drawn.
 */

export type SectionTypeName =
  | 'HERO'
  | 'PAGE_HEADER'
  | 'RICH_TEXT'
  | 'TEXT_WITH_IMAGE'
  | 'FEATURED_CATEGORIES'
  | 'FEATURED_PRODUCTS'
  | 'DISCOUNTED_PRODUCTS'
  | 'PRODUCT_LIST'
  | 'BENEFITS'
  | 'STATISTICS'
  | 'TESTIMONIALS'
  | 'PARTNERS'
  | 'LATEST_POSTS'
  | 'FAQ'
  | 'GALLERY'
  | 'TIMELINE'
  | 'TEAM'
  | 'CTA_BANNER'
  | 'CONTACT_FORM'
  | 'NEWSLETTER'
  | 'MAP'
  | 'SPACER'
  | 'HTML_EMBED';

export type BackgroundStyle = 'DEFAULT' | 'MUTED' | 'DARK' | 'ACCENT' | 'IMAGE';

export interface PublicSection {
  id: number;
  type: SectionTypeName;
  variant: string;
  eyebrow: string | null;
  title: string | null;
  subtitle: string | null;
  /** Sanitised server-side on write, safe to render as markup. */
  content: string | null;
  mediaUrl: string | null;
  mediaIsVideo: boolean;
  backgroundMediaUrl: string | null;
  backgroundMediaIsVideo: boolean;
  buttonText: string | null;
  buttonUrl: string | null;
  secondaryButtonText: string | null;
  secondaryButtonUrl: string | null;
  backgroundStyle: BackgroundStyle;
  /** Section-specific knobs: limit, showCountdown, columns, categoryId… */
  configuration: Record<string, unknown>;
}

export interface PublicPage {
  code: string;
  slug: string;
  title: string;
  shortDescription: string | null;
  coverUrl: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  canonicalUrl: string | null;
  allowIndexing: boolean;
  ogUrl: string | null;
  layout: string;
  sections: PublicSection[];
}

/** Every section component receives exactly this. */
export interface SectionProps {
  section: PublicSection;
  /** Page id, so scoped content (FAQ) can be fetched for this page only. */
  pageCode?: string;
}

// ---------------------------------------------------------------------------
// Configuration readers
//
// `configuration` is free-form JSONB an admin can edit as raw JSON, so every read
// is defensive: a mistyped value must degrade to a sane default rather than crash
// the whole page.
// ---------------------------------------------------------------------------

export function configNumber(
  section: PublicSection,
  key: string,
  fallback: number,
  max = 48,
): number {
  const raw = section.configuration?.[key];
  const value = typeof raw === 'number' ? raw : Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    return fallback;
  }
  return Math.min(Math.floor(value), max);
}

export function configBoolean(section: PublicSection, key: string, fallback = false): boolean {
  const raw = section.configuration?.[key];
  return typeof raw === 'boolean' ? raw : fallback;
}

export function configString(
  section: PublicSection,
  key: string,
  fallback: string | null = null,
): string | null {
  const raw = section.configuration?.[key];
  return typeof raw === 'string' && raw.trim() ? raw : fallback;
}

export function configArray<T = unknown>(section: PublicSection, key: string): T[] {
  const raw = section.configuration?.[key];
  return Array.isArray(raw) ? (raw as T[]) : [];
}
