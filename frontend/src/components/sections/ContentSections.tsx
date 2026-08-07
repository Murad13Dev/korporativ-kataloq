import clsx from 'clsx';
import { isInverted, SectionButton, SectionShell } from './SectionShell';
import { ScrollRail } from '@/components/ui/ScrollRail';
import { configArray, configNumber, type SectionProps } from './types';

/**
 * Sections whose content comes entirely from the section row itself — no extra fetch.
 */

// ---------------------------------------------------------------------------
// HERO
// ---------------------------------------------------------------------------

export function HeroSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const isSplit = section.variant === 'SPLIT';
  const isCentered = section.variant === 'CENTERED';
  const badges = configArray<string>(section, 'badges');

  const copy = (
    <div className={clsx(isCentered && 'mx-auto max-w-3xl text-center')}>
      {section.eyebrow && (
        <span
          className={clsx(
            'mb-3 inline-block text-sm font-semibold uppercase tracking-wide',
            inverted ? 'text-white/70' : 'text-[var(--color-primary)]',
          )}
        >
          {section.eyebrow}
        </span>
      )}

      {section.title && (
        <h1
          className={clsx(
            'text-4xl font-bold leading-[1.08] tracking-tight lg:text-6xl',
            inverted && 'text-white',
          )}
        >
          {section.title}
        </h1>
      )}

      {section.subtitle && (
        <p
          className={clsx(
            'mt-5 max-w-xl text-lg leading-relaxed',
            isCentered && 'mx-auto',
            inverted ? 'text-white/80' : 'text-[var(--color-body)]',
          )}
        >
          {section.subtitle}
        </p>
      )}

      <div className={clsx('mt-8 flex flex-wrap gap-3', isCentered && 'justify-center')}>
        <SectionButton text={section.buttonText} url={section.buttonUrl} inverted={inverted} />
        <SectionButton
          text={section.secondaryButtonText}
          url={section.secondaryButtonUrl}
          variant="secondary"
          inverted={inverted}
        />
      </div>

      {badges.length > 0 && (
        <ul
          className={clsx(
            'mt-7 flex flex-wrap gap-x-5 gap-y-2 text-sm',
            isCentered && 'justify-center',
            inverted ? 'text-white/75' : 'text-[var(--color-body)]',
          )}
        >
          {badges.map((badge) => (
            <li key={badge} className="flex items-center gap-1.5">
              <span
                className={clsx(
                  'h-1.5 w-1.5 rounded-full',
                  inverted ? 'bg-white/60' : 'bg-[var(--color-success)]',
                )}
                aria-hidden="true"
              />
              {badge}
            </li>
          ))}
        </ul>
      )}
    </div>
  );

  return (
    <SectionShell section={section} showHeader={false} compact={false}>
      {isSplit && section.mediaUrl ? (
        <div className="grid items-center gap-10 lg:grid-cols-2">
          {copy}
          {section.mediaIsVideo ? (
            <video
              src={section.mediaUrl}
              autoPlay
              muted
              loop
              playsInline
              className="w-full rounded-[var(--radius-card)] object-cover shadow-[var(--shadow-card)]"
            />
          ) : (
            <img
              src={section.mediaUrl}
              alt=""
              /* The hero image is above the fold — never lazy. */
              loading="eager"
              fetchPriority="high"
              className="w-full rounded-[var(--radius-card)] object-cover shadow-[var(--shadow-card)]"
            />
          )}
        </div>
      ) : (
        copy
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// PAGE_HEADER
// ---------------------------------------------------------------------------

export function PageHeaderSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);

  return (
    <SectionShell section={section} showHeader={false} compact>
      <div
        className={clsx(
          section.variant === 'CENTERED' && 'mx-auto max-w-2xl text-center',
          section.variant === 'WITH_IMAGE' && section.mediaUrl && 'grid items-center gap-8 lg:grid-cols-2',
        )}
      >
        <div>
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
            <h1
              className={clsx(
                'text-3xl font-bold tracking-tight lg:text-5xl',
                inverted && 'text-white',
              )}
            >
              {section.title}
            </h1>
          )}
          {section.subtitle && (
            <p
              className={clsx(
                'mt-3 text-lg leading-relaxed',
                inverted ? 'text-white/80' : 'text-[var(--color-body)]',
              )}
            >
              {section.subtitle}
            </p>
          )}
        </div>

        {section.variant === 'WITH_IMAGE' && section.mediaUrl && (
          <img
            src={section.mediaUrl}
            alt=""
            loading="eager"
            className="w-full rounded-[var(--radius-card)] object-cover"
          />
        )}
      </div>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// RICH_TEXT
// ---------------------------------------------------------------------------

export function RichTextSection({ section }: SectionProps) {
  if (!section.content) {
    return null;
  }

  return (
    <SectionShell
      section={section}
      containerClassName={clsx(
        section.variant === 'NARROW' && 'mx-auto max-w-2xl',
        section.variant === 'TWO_COLUMN' && 'columns-1 gap-10 md:columns-2',
      )}
    >
      {/*
        Sanitised on write by the OWASP policy in HtmlSanitizer, so this is safe.
        Nothing unsanitised can reach the database.
      */}
      <div className="prose-content" dangerouslySetInnerHTML={{ __html: section.content }} />
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// TEXT_WITH_IMAGE
// ---------------------------------------------------------------------------

export function TextWithImageSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const imageLeft = section.variant === 'IMAGE_LEFT';

  return (
    <SectionShell section={section} showHeader={false}>
      <div className="grid items-center gap-10 lg:grid-cols-2">
        {section.mediaUrl && (
          <img
            src={section.mediaUrl}
            alt=""
            loading="lazy"
            className={clsx(
              'w-full rounded-[var(--radius-card)] object-cover',
              imageLeft ? 'lg:order-1' : 'lg:order-2',
            )}
          />
        )}

        <div className={clsx(imageLeft ? 'lg:order-2' : 'lg:order-1')}>
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
              className={clsx(
                'text-2xl font-bold tracking-tight lg:text-3xl',
                inverted && 'text-white',
              )}
            >
              {section.title}
            </h2>
          )}
          {section.content && (
            <div
              className={clsx('prose-content mt-4', inverted && 'text-white/80')}
              dangerouslySetInnerHTML={{ __html: section.content }}
            />
          )}
          <div className="mt-6">
            <SectionButton text={section.buttonText} url={section.buttonUrl} inverted={inverted} />
          </div>
        </div>
      </div>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// CTA_BANNER
// ---------------------------------------------------------------------------

export function CtaBannerSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const isSplit = section.variant === 'SPLIT';

  return (
    <SectionShell section={section} showHeader={false} compact={section.variant === 'COMPACT'}>
      <div
        className={clsx(
          isSplit
            ? 'flex flex-wrap items-center justify-between gap-6'
            : 'mx-auto max-w-2xl text-center',
        )}
      >
        <div className={clsx(isSplit && 'min-w-[280px] flex-1')}>
          {section.title && (
            <h2
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
                'mt-2 text-base',
                inverted ? 'text-white/80' : 'text-[var(--color-body)]',
              )}
            >
              {section.subtitle}
            </p>
          )}
        </div>

        <div className={clsx('flex flex-wrap gap-3', !isSplit && 'mt-7 justify-center')}>
          <SectionButton text={section.buttonText} url={section.buttonUrl} inverted={inverted} />
          <SectionButton
            text={section.secondaryButtonText}
            url={section.secondaryButtonUrl}
            variant="secondary"
            inverted={inverted}
          />
        </div>
      </div>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// GALLERY
// ---------------------------------------------------------------------------

interface GalleryImage {
  url: string;
  alt?: string;
}

export function GallerySection({ section }: SectionProps) {
  const images = configArray<GalleryImage>(section, 'images').filter((image) => image?.url);

  if (images.length === 0) {
    return null;
  }

  const items = images.map((image, index) => (
    <img
      key={`${image.url}-${index}`}
      src={image.url}
      alt={image.alt ?? ''}
      loading="lazy"
      className={clsx(
        'w-full rounded-[var(--radius-card)] object-cover',
        section.variant === 'CAROUSEL' && 'h-64 w-80 shrink-0',
        section.variant === 'GRID' && 'aspect-[4/3]',
      )}
    />
  ));

  return (
    <SectionShell section={section}>
      {section.variant === 'CAROUSEL' ? (
        <ScrollRail inverted={isInverted(section.backgroundStyle)}>{items}</ScrollRail>
      ) : (
        <div
          className={clsx(
            section.variant === 'MASONRY'
              ? 'columns-2 gap-4 lg:columns-3 [&>*]:mb-4'
              : 'grid grid-cols-2 gap-4 lg:grid-cols-3',
          )}
        >
          {items}
        </div>
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// TIMELINE
// ---------------------------------------------------------------------------

interface TimelineEntry {
  year?: string;
  title?: string;
  description?: string;
}

export function TimelineSection({ section }: SectionProps) {
  const entries = configArray<TimelineEntry>(section, 'entries').filter(Boolean);
  const inverted = isInverted(section.backgroundStyle);

  if (entries.length === 0) {
    return null;
  }

  const horizontal = section.variant === 'HORIZONTAL';

  return (
    <SectionShell section={section}>
      <ol
        className={clsx(
          horizontal
            ? 'grid gap-6 sm:grid-cols-2 lg:grid-cols-4'
            : 'relative space-y-8 ps-6',
        )}
      >
        {/* Vertical variant draws a connecting rail behind the markers. */}
        {!horizontal && (
          <span
            className="absolute inset-y-0 start-[3px] w-px bg-[var(--color-border)]"
            aria-hidden="true"
          />
        )}

        {entries.map((entry, index) => (
          <li key={index} className={clsx(!horizontal && 'relative')}>
            {!horizontal && (
              <span
                className="absolute -start-6 top-1.5 h-2 w-2 rounded-full bg-[var(--color-primary)] ring-4 ring-[var(--color-canvas)]"
                aria-hidden="true"
              />
            )}
            {horizontal && (
              <span
                className="mb-3 block h-2 w-2 rounded-full bg-[var(--color-primary)]"
                aria-hidden="true"
              />
            )}

            {entry.year && (
              <span
                className={clsx(
                  'block font-display text-lg font-bold',
                  inverted ? 'text-white' : 'text-[var(--color-primary)]',
                )}
              >
                {entry.year}
              </span>
            )}
            {entry.title && (
              <h3
                className={clsx(
                  'mt-0.5 text-base font-semibold',
                  inverted ? 'text-white' : 'text-[var(--color-heading)]',
                )}
              >
                {entry.title}
              </h3>
            )}
            {entry.description && (
              <p
                className={clsx(
                  'mt-1 text-sm leading-relaxed',
                  inverted ? 'text-white/75' : 'text-[var(--color-body)]',
                )}
              >
                {entry.description}
              </p>
            )}
          </li>
        ))}
      </ol>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// TEAM
// ---------------------------------------------------------------------------

interface TeamMember {
  name?: string;
  position?: string;
  photoUrl?: string;
  bio?: string;
}

export function TeamSection({ section }: SectionProps) {
  const members = configArray<TeamMember>(section, 'members').filter((member) => member?.name);

  if (members.length === 0) {
    return null;
  }

  const isCarousel = section.variant === 'CAROUSEL';

  const items = members.map((member, index) => (
    <article key={index} className={clsx('card overflow-hidden', isCarousel && 'w-64 shrink-0')}>
      <div className="aspect-square bg-[var(--color-muted)]">
        {member.photoUrl && (
          <img
            src={member.photoUrl}
            alt={member.name ?? ''}
            loading="lazy"
            className="h-full w-full object-cover"
          />
        )}
      </div>
      <div className="p-4">
        <h3 className="text-sm font-semibold text-[var(--color-heading)]">{member.name}</h3>
        {member.position && (
          <p className="text-xs text-[var(--color-primary)]">{member.position}</p>
        )}
        {member.bio && (
          <p className="mt-2 line-clamp-3 text-xs text-[var(--color-body)]">{member.bio}</p>
        )}
      </div>
    </article>
  ));

  return (
    <SectionShell section={section}>
      {isCarousel ? (
        <ScrollRail inverted={isInverted(section.backgroundStyle)}>{items}</ScrollRail>
      ) : (
        <div className="grid grid-cols-2 gap-5 lg:grid-cols-4">{items}</div>
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// HTML_EMBED
// ---------------------------------------------------------------------------

export function HtmlEmbedSection({ section }: SectionProps) {
  if (!section.content) {
    return null;
  }

  return (
    <SectionShell
      section={section}
      containerClassName={clsx(section.variant === 'NARROW' && 'mx-auto max-w-2xl')}
    >
      <div className="prose-content" dangerouslySetInnerHTML={{ __html: section.content }} />
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// SPACER
// ---------------------------------------------------------------------------

export function SpacerSection({ section }: SectionProps) {
  const heights: Record<string, string> = {
    SMALL: 'h-8',
    MEDIUM: 'h-16',
    LARGE: 'h-28',
  };
  const custom = configNumber(section, 'height', 0, 400);

  return (
    <div
      aria-hidden="true"
      className={custom > 0 ? undefined : (heights[section.variant] ?? heights.MEDIUM)}
      style={custom > 0 ? { height: `${custom}px` } : undefined}
    />
  );
}
