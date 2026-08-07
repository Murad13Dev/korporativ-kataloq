import type { SiteConfiguration, ThemeConfig } from '@/types/api';

/**
 * Applies the published site configuration to the document as CSS custom properties.
 *
 * This is the mechanism that makes the platform white-label: `theme.css` never hard-codes
 * a colour, it reads `--ctl-*`, and this function is the only thing that writes them.
 * A client changing their brand in the admin panel repaints the whole storefront with no
 * rebuild and no per-client CSS file.
 */

/** Guards a value before it reaches a CSS context — configuration is admin-supplied. */
const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

function safeColor(value: string | undefined, fallback: string): string {
  return value && HEX_COLOR.test(value) ? value : fallback;
}

function safePx(value: number | undefined, fallback: number, min: number, max: number): string {
  const resolved = typeof value === 'number' && value >= min && value <= max ? value : fallback;
  return `${resolved}px`;
}

/**
 * Font family names go into a CSS declaration, so anything that could terminate the
 * declaration or start a new one is stripped rather than escaped.
 */
function safeFont(value: string | undefined, fallback: string): string {
  if (!value || !/^[a-z0-9 _-]{1,48}$/i.test(value)) {
    return fallback;
  }
  return value;
}

export function applyTheme(theme: ThemeConfig): void {
  const root = document.documentElement;

  const tokens: Record<string, string> = {
    '--ctl-primary': safeColor(theme.primaryColor, '#4f46e5'),
    '--ctl-primary-hover': safeColor(theme.primaryHoverColor, darken(theme.primaryColor, 8)),
    '--ctl-primary-contrast': safeColor(theme.primaryContrastColor, contrastOn(theme.primaryColor)),
    '--ctl-secondary': safeColor(theme.secondaryColor, '#0f172a'),
    '--ctl-accent': safeColor(theme.accentColor, '#06b6d4'),
    '--ctl-sale': safeColor(theme.saleColor, '#f43f5e'),
    '--ctl-success': safeColor(theme.successColor, '#10b981'),
    '--ctl-warning': safeColor(theme.warningColor, '#f59e0b'),
    '--ctl-danger': safeColor(theme.dangerColor, '#ef4444'),

    '--ctl-background': safeColor(theme.backgroundColor, '#ffffff'),
    '--ctl-surface': safeColor(theme.backgroundColor, '#ffffff'),
    '--ctl-section-background': safeColor(theme.sectionBackgroundColor, '#f8fafc'),
    '--ctl-border': safeColor(theme.borderColor, '#e2e8f0'),

    '--ctl-heading': safeColor(theme.headingColor, '#0f172a'),
    '--ctl-text': safeColor(theme.textColor, '#475569'),
    '--ctl-muted-text': safeColor(theme.mutedTextColor, '#94a3b8'),

    '--ctl-footer-background': safeColor(theme.footerBackgroundColor, '#0b1120'),
    '--ctl-footer-text': safeColor(theme.footerTextColor, '#94a3b8'),

    '--ctl-font-family': safeFont(theme.fontFamily, 'Inter'),
    '--ctl-heading-font': safeFont(theme.headingFontFamily, 'Sora'),
    '--ctl-base-font-size': safePx(theme.baseFontSize, 16, 12, 20),

    '--ctl-button-radius': safePx(theme.buttonRadius, 10, 0, 40),
    '--ctl-card-radius': safePx(theme.cardRadius, 16, 0, 40),
    '--ctl-input-radius': safePx(theme.inputRadius, 10, 0, 40),
    '--ctl-container-width': safePx(theme.containerWidth, 1280, 960, 1920),
  };

  for (const [name, value] of Object.entries(tokens)) {
    root.style.setProperty(name, value);
  }

  // Keeps the mobile browser chrome in step with the site's own header.
  const themeColorMeta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (themeColorMeta) {
    themeColorMeta.content = tokens['--ctl-secondary'] ?? '#0f172a';
  }
}

/** Title, description and favicon from the published configuration. */
export function applyDocumentMeta(config: SiteConfiguration): void {
  const title = config.seo?.defaultTitle ?? config.company?.name ?? 'Kataloq';
  document.title = title;

  const description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
  if (description && config.seo?.defaultDescription) {
    description.content = config.seo.defaultDescription;
  }

  const favicon = document.querySelector<HTMLLinkElement>('#app-favicon');
  if (favicon && config.branding?.faviconUrl) {
    favicon.href = config.branding.faviconUrl;
  }
}

/**
 * Per-page title, following the configured template ("%s | Şirkət").
 * Returns a cleanup function so a route can restore the site title on unmount.
 */
export function setPageTitle(config: SiteConfiguration | undefined, pageTitle?: string): () => void {
  const previous = document.title;
  const siteTitle = config?.seo?.defaultTitle ?? config?.company?.name ?? 'Kataloq';

  if (!pageTitle) {
    document.title = siteTitle;
  } else {
    const template = config?.seo?.titleTemplate;
    document.title = template?.includes('%s')
      ? template.replace('%s', pageTitle)
      : `${pageTitle} | ${siteTitle}`;
  }

  return () => {
    document.title = previous;
  };
}

export interface PageMeta {
  title: string;
  description?: string | null;
  /** Defaults to the current URL when omitted — every route needs a canonical, not just CMS pages. */
  canonicalUrl?: string | null;
  ogImage?: string | null;
  /** Defaults to indexable; only a CMS page can explicitly opt out. */
  allowIndexing?: boolean;
}

/**
 * Full per-route SEO block: title, description, canonical, robots, OG title/image.
 *
 * There is exactly one page mounted at a time, so this writes straight into the existing
 * `<head>` elements rather than through a helper library, and hands back a cleanup
 * function that restores what was there before — a route that unmounts must not leave
 * its canonical URL or OG image behind for the next one.
 */
export function setPageMeta(config: SiteConfiguration | undefined, meta: PageMeta): () => void {
  const restoreTitle = setPageTitle(config, meta.title);

  const description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
  const previousDescription = description?.content;
  if (description && meta.description) {
    description.content = meta.description;
  }

  const robots = ensureMeta('robots');
  const previousRobots = robots.content;
  robots.content = meta.allowIndexing === false ? 'noindex, nofollow' : 'index, follow';

  const canonical = ensureLink('canonical');
  const previousCanonical = canonical.href;
  canonical.href = meta.canonicalUrl ?? window.location.href;

  const ogTitle = ensureMeta('og:title', 'property');
  const previousOgTitle = ogTitle.content;
  ogTitle.content = meta.title;

  const ogImage = ensureMeta('og:image', 'property');
  const previousOgImage = ogImage.content;
  if (meta.ogImage) {
    ogImage.content = meta.ogImage;
  }

  return () => {
    restoreTitle();
    if (description && previousDescription !== undefined) {
      description.content = previousDescription;
    }
    robots.content = previousRobots;
    canonical.href = previousCanonical;
    ogTitle.content = previousOgTitle;
    ogImage.content = previousOgImage;
  };
}

/**
 * Injects one or more schema.org objects as `<script type="application/ld+json">` tags,
 * keyed so a route can own its own tag without clobbering another route's (or the
 * layout's site-wide Organization tag, which lives under its own key and outlives any
 * single page). Returns a cleanup function that removes exactly what it added.
 */
export function setJsonLd(key: string, data: object | object[]): () => void {
  const id = `ld-json-${key}`;
  let script = document.getElementById(id) as HTMLScriptElement | null;
  if (!script) {
    script = document.createElement('script');
    script.id = id;
    script.type = 'application/ld+json';
    document.head.appendChild(script);
  }
  script.textContent = JSON.stringify(data);

  return () => {
    document.getElementById(id)?.remove();
  };
}

function ensureMeta(name: string, attribute: 'name' | 'property' = 'name'): HTMLMetaElement {
  let element = document.querySelector<HTMLMetaElement>(`meta[${attribute}="${name}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, name);
    document.head.appendChild(element);
  }
  return element;
}

function ensureLink(rel: string): HTMLLinkElement {
  let element = document.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
  if (!element) {
    element = document.createElement('link');
    element.rel = rel;
    document.head.appendChild(element);
  }
  return element;
}

// ---------------------------------------------------------------------------
// Small colour helpers
//
// Deliberately naive sRGB maths: these only ever produce a *fallback* when the
// admin has not set an explicit hover or contrast colour, so perceptual accuracy
// matters less than never producing an unreadable pair.
// ---------------------------------------------------------------------------

function parseHex(hex: string | undefined): [number, number, number] | null {
  if (!hex || !HEX_COLOR.test(hex)) {
    return null;
  }
  let value = hex.slice(1);
  if (value.length === 3) {
    value = value.split('').map((char) => char + char).join('');
  }
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ];
}

function darken(hex: string | undefined, percent: number): string {
  const rgb = parseHex(hex);
  if (!rgb) {
    return '#4338ca';
  }
  const factor = 1 - percent / 100;
  const [r, g, b] = rgb.map((channel) => Math.max(0, Math.round(channel * factor)));
  return `#${[r, g, b].map((c) => c!.toString(16).padStart(2, '0')).join('')}`;
}

/**
 * Picks black or white text for a background using the WCAG relative-luminance
 * threshold, so a light brand colour does not end up with white text on it.
 */
function contrastOn(hex: string | undefined): string {
  const rgb = parseHex(hex);
  if (!rgb) {
    return '#ffffff';
  }
  const [r, g, b] = rgb.map((channel) => {
    const normalised = channel / 255;
    return normalised <= 0.03928
      ? normalised / 12.92
      : Math.pow((normalised + 0.055) / 1.055, 2.4);
  }) as [number, number, number];

  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 0.45 ? '#0f172a' : '#ffffff';
}

/**
 * Ribbon colour for a discount badge style. Styles map to *theme tokens*, never to
 * hard-coded colours, so campaign ribbons stay on brand for every installation.
 */
export function ribbonColor(
  badgeStyle: string | null | undefined,
  explicitColor: string | null | undefined,
): string {
  if (explicitColor && HEX_COLOR.test(explicitColor)) {
    return explicitColor;
  }
  switch (badgeStyle) {
    case 'HOT':
      return 'var(--color-danger)';
    case 'SEASONAL':
      return 'var(--color-accent)';
    case 'CLEARANCE':
      return 'var(--color-secondary)';
    case 'BUNDLE':
      return 'var(--color-primary)';
    case 'SALE':
    case 'CUSTOM':
    default:
      return 'var(--color-sale)';
  }
}
