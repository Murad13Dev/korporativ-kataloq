import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import {
  Award,
  Box,
  ChevronDown,
  Headset,
  MapPin,
  Package,
  ShieldCheck,
  Star,
  Truck,
  Users,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { isInverted, SectionButton, SectionShell } from './SectionShell';
import { RevealGroup } from '@/components/ui/Reveal';
import { AnimatedCounter } from '@/components/ui/AnimatedCounter';
import { getData } from '@/lib/api';
import { configNumber, configString, type SectionProps } from './types';

/**
 * Social-proof and support sections, backed by the corporate content blocks.
 */

// Admin-chosen icon names map to a fixed set — an arbitrary string cannot become a
// component, and a missing name degrades to a neutral default.
const ICONS: Record<string, LucideIcon> = {
  package: Package,
  box: Box,
  'shield-check': ShieldCheck,
  truck: Truck,
  headset: Headset,
  star: Star,
  users: Users,
  'map-pin': MapPin,
  award: Award,
  zap: Zap,
};

export function iconFor(name: string | null): LucideIcon {
  return (name && ICONS[name]) || ShieldCheck;
}

// ---------------------------------------------------------------------------
// BENEFITS
// ---------------------------------------------------------------------------

export interface Benefit {
  id: number;
  icon: string | null;
  iconUrl: string | null;
  title: string;
  description: string | null;
}

export function BenefitsSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const columns = configNumber(section, 'columns', 4, 6);

  const query = useQuery({
    queryKey: ['sections', 'benefits'],
    queryFn: () => getData<Benefit[]>('/public/benefits'),
    staleTime: 15 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  const isList = section.variant === 'ICON_LIST';
  const isStrip = section.variant === 'STRIP';

  if (isStrip) {
    return (
      <SectionShell section={section} showHeader={false} compact>
        <ul className="flex flex-wrap items-center justify-center gap-x-8 gap-y-3 text-sm">
          {query.isLoading
            ? Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="skeleton h-5 w-32 rounded-full" />
              ))
            : query.data!.slice(0, 5).map((benefit) => {
                const Icon = iconFor(benefit.icon);
                return (
                  <li key={benefit.id} className="flex items-center gap-2.5">
                    {benefit.iconUrl ? (
                      <img src={benefit.iconUrl} alt="" className="h-5 w-5 shrink-0 object-contain" />
                    ) : (
                      <Icon
                        size={18}
                        className={inverted ? 'shrink-0 text-white/80' : 'shrink-0 text-[var(--color-primary)]'}
                        aria-hidden="true"
                      />
                    )}
                    <span className={clsx('font-medium', inverted ? 'text-white' : 'text-[var(--color-heading)]')}>
                      {benefit.title}
                    </span>
                  </li>
                );
              })}
        </ul>
      </SectionShell>
    );
  }

  const gridClassName = clsx(
    isList ? 'space-y-5' : 'grid gap-5',
    !isList && columns >= 4 && 'grid-cols-2 lg:grid-cols-4',
    !isList && columns === 3 && 'grid-cols-1 sm:grid-cols-3',
    !isList && columns <= 2 && 'grid-cols-1 sm:grid-cols-2',
  );

  if (query.isLoading) {
    return (
      <SectionShell section={section}>
        <div className={gridClassName}>
          {Array.from({ length: columns }).map((_, index) => (
            <div key={index} className="skeleton h-28 rounded-[var(--radius-card)]" />
          ))}
        </div>
      </SectionShell>
    );
  }

  return (
    <SectionShell section={section}>
      <RevealGroup className={gridClassName} staggerDelay={0.06}>
        {query.data!.map((benefit) => {
              const Icon = iconFor(benefit.icon);
              return (
                <div
                  key={benefit.id}
                  className={clsx(
                    'group',
                    isList
                      ? 'flex items-start gap-4'
                      : clsx(
                          'relative overflow-hidden p-6 transition-all duration-300',
                          inverted
                            ? 'rounded-[var(--radius-card)] border border-white/15 bg-white/8 hover:bg-white/12'
                            : 'card card-elevated',
                        ),
                  )}
                >
                  <span
                    className={clsx(
                      'flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl transition-colors duration-300',
                      inverted
                        ? 'bg-white/15 text-white group-hover:bg-white group-hover:text-[var(--color-primary)]'
                        : 'bg-[var(--color-primary)]/10 text-[var(--color-primary)] group-hover:bg-[var(--color-primary)] group-hover:text-white',
                      !isList && 'mb-4',
                    )}
                  >
                    {benefit.iconUrl ? (
                      <img src={benefit.iconUrl} alt="" className="h-6 w-6 object-contain" />
                    ) : (
                      <Icon size={22} aria-hidden="true" />
                    )}
                  </span>

                  <div>
                    <h3
                      className={clsx(
                        'text-base font-semibold',
                        inverted ? 'text-white' : 'text-[var(--color-heading)]',
                      )}
                    >
                      {benefit.title}
                    </h3>
                    {benefit.description && (
                      <p
                        className={clsx(
                          'mt-1.5 text-sm leading-relaxed',
                          inverted ? 'text-white/75' : 'text-[var(--color-body)]',
                        )}
                      >
                        {benefit.description}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
      </RevealGroup>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// STATISTICS
// ---------------------------------------------------------------------------

interface Statistic {
  id: number;
  icon: string | null;
  value: string;
  suffix: string | null;
  label: string;
}

export function StatisticsSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);

  const query = useQuery({
    queryKey: ['sections', 'statistics'],
    queryFn: () => getData<Statistic[]>('/public/statistics'),
    staleTime: 15 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  if (query.isLoading) {
    return (
      <SectionShell section={section}>
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4 lg:gap-6">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="skeleton h-32 rounded-[var(--radius-card)]" />
          ))}
        </div>
      </SectionShell>
    );
  }

  return (
    <SectionShell section={section}>
      <RevealGroup className="grid grid-cols-2 gap-4 lg:grid-cols-4 lg:gap-6" staggerDelay={0.08}>
        {query.data!.map((statistic) => {
          const Icon = iconFor(statistic.icon);
          return (
            <div
              key={statistic.id}
              className={clsx(
                'group relative overflow-hidden rounded-[var(--radius-card)] p-5 text-center transition-all duration-300 hover:-translate-y-1 lg:p-6',
                inverted
                  ? 'border border-white/12 bg-white/6 hover:border-white/25 hover:bg-white/10'
                  : 'card card-elevated',
              )}
            >
              <span
                className={clsx(
                  'mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full transition-colors duration-300',
                  inverted
                    ? 'bg-white/12 text-white group-hover:bg-[var(--color-primary)] group-hover:text-white'
                    : 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
                )}
              >
                <Icon size={20} aria-hidden="true" />
              </span>
              <p
                className={clsx(
                  'font-display text-3xl font-bold tabular lg:text-4xl',
                  inverted ? 'text-white' : 'text-[var(--color-heading)]',
                )}
              >
                <AnimatedCounter value={statistic.value} />
                {statistic.suffix && (
                  <span className={inverted ? 'text-white/70' : 'text-[var(--color-primary)]'}>
                    {statistic.suffix}
                  </span>
                )}
              </p>
              <span
                className={clsx(
                  'mx-auto mt-2 block h-px w-8 rounded-full',
                  inverted ? 'bg-white/20' : 'bg-[var(--color-primary)]/25',
                )}
              />
              <p
                className={clsx(
                  'mt-2 text-sm',
                  inverted ? 'text-white/70' : 'text-[var(--color-body)]',
                )}
              >
                {statistic.label}
              </p>
            </div>
          );
        })}
      </RevealGroup>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// TESTIMONIALS
// ---------------------------------------------------------------------------

interface Testimonial {
  id: number;
  personName: string;
  position: string | null;
  companyName: string | null;
  photoUrl: string | null;
  rating: number | null;
  content: string;
  featured: boolean;
}

export function TestimonialsSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const limit = configNumber(section, 'limit', 6, 24);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduceMotion = useReducedMotion();

  const query = useQuery({
    queryKey: ['sections', 'testimonials', limit],
    queryFn: () => getData<Testimonial[]>('/public/testimonials', { limit }),
    staleTime: 15 * 60 * 1000,
  });

  const testimonials = query.data ?? [];
  const isSlider = section.variant === 'SLIDER' || section.variant === 'SINGLE';

  // Auto-advance the slider — paused on hover/focus so nobody loses their place
  // mid-read, and skipped entirely for reduced-motion or a single testimonial.
  useEffect(() => {
    if (!isSlider || reduceMotion || paused || testimonials.length < 2) {
      return;
    }
    const timer = window.setInterval(() => {
      setIndex((current) => (current + 1) % testimonials.length);
    }, 6000);
    return () => window.clearInterval(timer);
  }, [isSlider, reduceMotion, paused, testimonials.length]);

  if (!query.isLoading && testimonials.length === 0) {
    return null;
  }

  const current = testimonials[index % Math.max(testimonials.length, 1)];

  return (
    <SectionShell section={section}>
      {query.isLoading ? (
        <div className="skeleton h-40 rounded-[var(--radius-card)]" />
      ) : isSlider && current ? (
        <div
          className="mx-auto max-w-3xl"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onBlur={() => setPaused(false)}
        >
          <AnimatePresence mode="wait">
            <motion.div
              key={current.id}
              initial={reduceMotion ? undefined : { opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduceMotion ? undefined : { opacity: 0, y: -12 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <TestimonialCard testimonial={current} inverted={inverted} large />
            </motion.div>
          </AnimatePresence>

          {testimonials.length > 1 && (
            <div className="mt-6 flex justify-center gap-2" role="tablist" aria-label="Rəylər">
              {testimonials.map((testimonial, position) => (
                <button
                  key={testimonial.id}
                  type="button"
                  role="tab"
                  aria-selected={position === index}
                  aria-label={`Rəy ${position + 1}`}
                  onClick={() => setIndex(position)}
                  className={clsx(
                    'h-2 rounded-full transition-all',
                    position === index
                      ? inverted
                        ? 'w-6 bg-white'
                        : 'w-6 bg-[var(--color-primary)]'
                      : inverted
                        ? 'w-2 bg-white/30'
                        : 'w-2 bg-[var(--color-border)]',
                  )}
                />
              ))}
            </div>
          )}
        </div>
      ) : (
        <RevealGroup className="grid gap-5 md:grid-cols-2 lg:grid-cols-3" staggerDelay={0.06}>
          {testimonials.map((testimonial) => (
            <TestimonialCard
              key={testimonial.id}
              testimonial={testimonial}
              inverted={inverted}
            />
          ))}
        </RevealGroup>
      )}
    </SectionShell>
  );
}

function TestimonialCard({
  testimonial,
  inverted,
  large,
}: {
  testimonial: Testimonial;
  inverted: boolean;
  large?: boolean;
}) {
  return (
    <figure
      className={clsx(
        'rounded-[var(--radius-card)] p-6',
        inverted ? 'bg-white/8' : 'card',
        large && 'text-center',
      )}
    >
      {testimonial.rating && (
        <div
          className={clsx('mb-3 flex gap-0.5', large && 'justify-center')}
          aria-label={`${testimonial.rating} ulduz`}
        >
          {Array.from({ length: 5 }).map((_, star) => (
            <Star
              key={star}
              size={15}
              aria-hidden="true"
              className={
                star < testimonial.rating!
                  ? 'fill-[var(--color-warning)] text-[var(--color-warning)]'
                  : inverted
                    ? 'text-white/25'
                    : 'text-[var(--color-border)]'
              }
            />
          ))}
        </div>
      )}

      <blockquote
        className={clsx(
          'leading-relaxed',
          large ? 'text-lg' : 'text-sm',
          inverted ? 'text-white/90' : 'text-[var(--color-body)]',
        )}
      >
        “{testimonial.content}”
      </blockquote>

      <figcaption
        className={clsx('mt-5 flex items-center gap-3', large && 'justify-center')}
      >
        {testimonial.photoUrl ? (
          <img
            src={testimonial.photoUrl}
            alt=""
            loading="lazy"
            className="h-11 w-11 rounded-full object-cover"
          />
        ) : (
          <span
            className={clsx(
              'flex h-11 w-11 items-center justify-center rounded-full text-sm font-bold',
              inverted ? 'bg-white/15 text-white' : 'bg-[var(--color-muted)] text-[var(--color-body)]',
            )}
          >
            {testimonial.personName.charAt(0)}
          </span>
        )}

        <span className={large ? 'text-start' : undefined}>
          <span
            className={clsx(
              'block text-sm font-semibold',
              inverted ? 'text-white' : 'text-[var(--color-heading)]',
            )}
          >
            {testimonial.personName}
          </span>
          {(testimonial.position || testimonial.companyName) && (
            <span
              className={clsx(
                'block text-xs',
                inverted ? 'text-white/60' : 'text-[var(--color-subtle)]',
              )}
            >
              {[testimonial.position, testimonial.companyName].filter(Boolean).join(', ')}
            </span>
          )}
        </span>
      </figcaption>
    </figure>
  );
}

// ---------------------------------------------------------------------------
// PARTNERS
// ---------------------------------------------------------------------------

interface Partner {
  id: number;
  name: string;
  logoUrl: string | null;
  websiteUrl: string | null;
}

export function PartnersSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);

  const query = useQuery({
    queryKey: ['sections', 'partners'],
    queryFn: () => getData<Partner[]>('/public/partners'),
    staleTime: 30 * 60 * 1000,
  });

  const partners = query.data ?? [];

  if (!query.isLoading && partners.length === 0) {
    return null;
  }

  const renderLogo = (partner: Partner) => {
    const logo = partner.logoUrl ? (
      <img
        src={partner.logoUrl}
        alt={partner.name}
        loading="lazy"
        /* Greyscale until hover keeps a wall of mismatched logos calm. */
        className={clsx(
          'h-9 w-auto max-w-[140px] object-contain transition-all',
          'opacity-60 grayscale hover:opacity-100 hover:grayscale-0',
          inverted && 'brightness-0 invert',
        )}
      />
    ) : (
      <span
        className={clsx(
          'text-base font-semibold',
          inverted ? 'text-white/60' : 'text-[var(--color-subtle)]',
        )}
      >
        {partner.name}
      </span>
    );

    return partner.websiteUrl ? (
      <a
        href={partner.websiteUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="flex shrink-0 items-center justify-center"
      >
        {logo}
      </a>
    ) : (
      <span className="flex shrink-0 items-center justify-center">{logo}</span>
    );
  };

  const isGrid = section.variant === 'GRID';
  // A short list loops too abruptly to read as a marquee — those stay a calm, static row.
  const canMarquee = !isGrid && partners.length >= 6;

  return (
    <SectionShell section={section}>
      {isGrid ? (
        <RevealGroup className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-6" staggerDelay={0.05}>
          {partners.map((partner) => (
            <div key={partner.id}>{renderLogo(partner)}</div>
          ))}
        </RevealGroup>
      ) : canMarquee ? (
        <div className="marquee-viewport">
          <div className="marquee-track">
            {/* Duplicated once so the track can scroll exactly -50% and loop seamlessly. */}
            {[...partners, ...partners].map((partner, index) => (
              <div key={`${partner.id}-${index}`}>{renderLogo(partner)}</div>
            ))}
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-center gap-x-10 gap-y-6">
          {partners.map((partner) => (
            <div key={partner.id}>{renderLogo(partner)}</div>
          ))}
        </div>
      )}
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// FAQ
// ---------------------------------------------------------------------------

interface Faq {
  id: number;
  category: string | null;
  question: string;
  answer: string;
}

export function FaqSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const limit = configNumber(section, 'limit', 12, 50);
  const category = configString(section, 'category');
  const [openId, setOpenId] = useState<number | null>(null);

  const query = useQuery({
    queryKey: ['sections', 'faqs', category],
    queryFn: () => getData<Faq[]>('/public/faqs', { category: category ?? undefined }),
    staleTime: 15 * 60 * 1000,
  });

  if (!query.isLoading && (!query.data || query.data.length === 0)) {
    return null;
  }

  const faqs = (query.data ?? []).slice(0, limit);

  return (
    <SectionShell
      section={section}
      containerClassName={section.variant === 'ACCORDION' ? 'mx-auto max-w-3xl' : undefined}
    >
      <div
        className={clsx(
          section.variant === 'TWO_COLUMN' && 'grid gap-4 md:grid-cols-2',
          section.variant === 'ACCORDION' && 'space-y-2',
        )}
      >
        {faqs.map((faq) => {
          const isOpen = openId === faq.id;
          return (
            <div
              key={faq.id}
              className={clsx(
                'overflow-hidden rounded-[var(--radius-card)] border',
                inverted ? 'border-white/15' : 'border-[var(--color-border)]',
              )}
            >
              <h3>
                <button
                  type="button"
                  onClick={() => setOpenId(isOpen ? null : faq.id)}
                  aria-expanded={isOpen}
                  aria-controls={`faq-${faq.id}`}
                  className={clsx(
                    'flex w-full items-center justify-between gap-4 px-5 py-4 text-start text-sm font-medium transition-colors',
                    inverted
                      ? 'text-white hover:bg-white/5'
                      : 'text-[var(--color-heading)] hover:bg-[var(--color-muted)]',
                  )}
                >
                  {faq.question}
                  <ChevronDown
                    size={17}
                    aria-hidden="true"
                    className={clsx(
                      'shrink-0 transition-transform duration-200',
                      isOpen && 'rotate-180',
                      inverted ? 'text-white/60' : 'text-[var(--color-subtle)]',
                    )}
                  />
                </button>
              </h3>

              <AnimatePresence initial={false}>
                {isOpen && (
                  <motion.div
                    key="content"
                    id={`faq-${faq.id}`}
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                    className="overflow-hidden"
                  >
                    <div
                      className={clsx(
                        'px-5 pb-4 text-sm leading-relaxed',
                        inverted ? 'text-white/80' : 'text-[var(--color-body)]',
                      )}
                    >
                      {faq.answer}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          );
        })}
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
