import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { ArrowRight } from 'lucide-react';
import type { BackgroundStyle, PublicSection } from './types';

/**
 * The frame every section shares: background treatment, vertical rhythm, container
 * width and the eyebrow/title/subtitle header.
 *
 * Centralising this is what keeps a page built from twelve different sections looking
 * like one design rather than twelve. A section component decides its *content*; the
 * shell decides everything about how it sits on the page.
 */

const BACKGROUND_CLASSES: Record<BackgroundStyle, string> = {
  DEFAULT: 'bg-[var(--color-canvas)]',
  MUTED: 'bg-[var(--color-muted)]',
  DARK: 'bg-[var(--color-secondary)] text-white',
  ACCENT: 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]',
  IMAGE: 'relative bg-[var(--color-secondary)] text-white',
};

/** Dark and accent backgrounds need their own heading/body colours. */
function isInverted(style: BackgroundStyle): boolean {
  return style === 'DARK' || style === 'ACCENT' || style === 'IMAGE';
}

export function SectionShell({
  section,
  children,
  containerClassName,
  showHeader = true,
  compact = false,
}: {
  section: PublicSection;
  children?: ReactNode;
  containerClassName?: string;
  showHeader?: boolean;
  compact?: boolean;
}) {
  const inverted = isInverted(section.backgroundStyle);
  const hasHeader =
    showHeader && Boolean(section.eyebrow || section.title || section.subtitle);

  return (
    <section
      className={clsx(
        BACKGROUND_CLASSES[section.backgroundStyle],
        compact ? 'py-10' : 'py-14 lg:py-20',
      )}
      aria-labelledby={section.title ? `section-${section.id}-title` : undefined}
    >
      {/* Background media sits behind a scrim so text stays legible whatever was uploaded. */}
      {section.backgroundStyle === 'IMAGE' && section.backgroundMediaUrl && (
        <>
          {section.backgroundMediaIsVideo ? (
            <video
              src={section.backgroundMediaUrl}
              autoPlay
              muted
              loop
              playsInline
              aria-hidden="true"
              className="absolute inset-0 h-full w-full object-cover"
            />
          ) : (
            <img
              src={section.backgroundMediaUrl}
              alt=""
              aria-hidden="true"
              className="absolute inset-0 h-full w-full object-cover"
            />
          )}
          <div className="absolute inset-0 bg-black/55" aria-hidden="true" />
        </>
      )}

      <div className={clsx('container-site', section.backgroundStyle === 'IMAGE' && 'relative')}>
        {hasHeader && (
          <SectionHeader section={section} inverted={inverted} />
        )}
        <div className={containerClassName}>{children}</div>
      </div>
    </section>
  );
}

export function SectionHeader({
  section,
  inverted,
  centered,
}: {
  section: PublicSection;
  inverted: boolean;
  centered?: boolean;
}) {
  const isCentered = centered ?? section.variant.includes('CENTERED');

  return (
    <header className={clsx('mb-8 lg:mb-12', isCentered && 'mx-auto max-w-2xl text-center')}>
      {section.eyebrow && (
        <span
          className={clsx(
            'mb-2 block text-sm font-semibold uppercase tracking-wide',
            inverted ? 'text-white/70' : 'text-[var(--color-primary)]',
          )}
        >
          {section.eyebrow}
        </span>
      )}

      {section.title && (
        <h2
          id={`section-${section.id}-title`}
          className={clsx(
            'text-2xl font-bold tracking-tight lg:text-3xl',
            inverted && 'text-white',
          )}
        >
          {section.title}
        </h2>
      )}

      {section.subtitle && (
        <p
          className={clsx(
            'mt-2.5 text-base leading-relaxed',
            inverted ? 'text-white/80' : 'text-[var(--color-body)]',
          )}
        >
          {section.subtitle}
        </p>
      )}
    </header>
  );
}

/**
 * Section call-to-action. Renders nothing when the admin left the button empty, which is
 * why every section can offer one without producing stray empty buttons.
 */
export function SectionButton({
  text,
  url,
  variant = 'primary',
  inverted = false,
  className,
}: {
  text: string | null;
  url: string | null;
  variant?: 'primary' | 'secondary';
  inverted?: boolean;
  className?: string;
}) {
  if (!text || !url) {
    return null;
  }

  const classes = clsx(
    'inline-flex items-center gap-2 rounded-[var(--radius-button)] px-6 py-3 text-sm font-semibold transition-colors',
    variant === 'primary'
      ? inverted
        ? 'bg-white text-[var(--color-secondary)] hover:bg-white/90'
        : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]'
      : inverted
        ? 'border border-white/40 text-white hover:bg-white/10'
        : 'border border-[var(--color-border)] text-[var(--color-heading)] hover:bg-[var(--color-muted)]',
    className,
  );

  // External links leave the SPA; internal ones must not trigger a full reload.
  const isExternal = /^https?:\/\//i.test(url);

  if (isExternal) {
    return (
      <a href={url} target="_blank" rel="noopener noreferrer" className={classes}>
        {text}
        <ArrowRight size={16} aria-hidden="true" />
      </a>
    );
  }

  return (
    <Link to={url} className={classes}>
      {text}
      <ArrowRight size={16} aria-hidden="true" />
    </Link>
  );
}

/**
 * Section-level loading placeholder. Keeps the page height stable while a data-backed
 * section fetches, so content below does not jump.
 */
export function SectionSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="skeleton h-48 rounded-[var(--radius-card)]" />
      ))}
    </div>
  );
}

export { isInverted };
