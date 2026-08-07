import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import clsx from 'clsx';
import { Check, Clock, Loader2, Mail, MapPin, Phone, Send } from 'lucide-react';
import { isInverted, SectionShell } from './SectionShell';
import { useToast } from '@/components/ui/Toast';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { ApiError, postVoid } from '@/lib/api';
import { configBoolean, configString, type SectionProps } from './types';

/**
 * Sections that submit something. Both post to public, rate-limited endpoints and both
 * confirm inline rather than navigating away — losing the page you were reading because
 * you signed up for a newsletter is a poor trade.
 */

// ---------------------------------------------------------------------------
// CONTACT_FORM
// ---------------------------------------------------------------------------

interface ContactForm {
  name: string;
  companyName: string;
  email: string;
  phone: string;
  subject: string;
  message: string;
  privacyAccepted: boolean;
  /** Honeypot — hidden from people, filled in by naive bots. Must stay empty. */
  website: string;
}

export function ContactFormSection({ section }: SectionProps) {
  const { company } = useSiteConfig();
  const toast = useToast();
  const [sent, setSent] = useState(false);

  const showInfo = configBoolean(section, 'showOffice', true);
  const showMap = configBoolean(section, 'showMap', false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ContactForm>();

  const submit = useMutation({
    mutationFn: (values: ContactForm) =>
      postVoid('/public/contact-requests', {
        ...values,
        requestType: 'CONTACT',
        sourceUrl: window.location.href,
      }),
    onSuccess: () => {
      setSent(true);
      reset();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const withInfo = section.variant === 'WITH_INFO' || section.variant === 'WITH_MAP';

  return (
    <SectionShell section={section}>
      <div className={clsx(withInfo && 'grid gap-8 lg:grid-cols-[1fr_320px]')}>
        {/* ---------------- form ---------------- */}
        <div className="card p-6">
          {sent ? (
            <div className="py-10 text-center">
              <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-success)]/12">
                <Check size={22} className="text-[var(--color-success)]" aria-hidden="true" />
              </span>
              <h3 className="mb-1.5 text-lg font-semibold">Mesajınız göndərildi</h3>
              <p className="text-sm text-[var(--color-body)]">
                Komandamız bir iş günü ərzində sizinlə əlaqə saxlayacaq.
              </p>
              <button
                type="button"
                onClick={() => setSent(false)}
                className="mt-5 text-sm font-medium text-[var(--color-primary)] hover:underline"
              >
                Yeni mesaj göndər
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit((values) => submit.mutate(values))} noValidate>
              {/*
                Honeypot. Hidden from sighted users by position and from assistive tech by
                aria-hidden + tabIndex, so only a script that reads the DOM will fill it.
                `display:none` is avoided because some bots skip hidden inputs.
              */}
              <div
                aria-hidden="true"
                className="absolute -left-[9999px] h-0 w-0 overflow-hidden"
              >
                <label htmlFor="website">Veb sayt</label>
                <input
                  id="website"
                  type="text"
                  tabIndex={-1}
                  autoComplete="off"
                  {...register('website')}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Ad və soyad" error={errors.name?.message} required>
                  <input
                    {...register('name', { required: 'Ad tələb olunur' })}
                    autoComplete="name"
                    className={inputClass(Boolean(errors.name))}
                  />
                </Field>

                <Field label="Şirkət">
                  <input
                    {...register('companyName')}
                    autoComplete="organization"
                    className={inputClass(false)}
                  />
                </Field>

                <Field label="E-poçt" error={errors.email?.message} required>
                  <input
                    {...register('email', {
                      required: 'E-poçt tələb olunur',
                      pattern: {
                        value: /^[^@\s]+@[^@\s]+\.[^@\s]+$/,
                        message: 'E-poçt formatı yanlışdır',
                      },
                    })}
                    type="email"
                    autoComplete="email"
                    className={inputClass(Boolean(errors.email))}
                  />
                </Field>

                <Field label="Telefon">
                  <input
                    {...register('phone')}
                    type="tel"
                    autoComplete="tel"
                    className={inputClass(false)}
                  />
                </Field>

                <Field label="Mövzu" className="sm:col-span-2">
                  <input {...register('subject')} className={inputClass(false)} />
                </Field>

                <Field
                  label="Mesaj"
                  error={errors.message?.message}
                  required
                  className="sm:col-span-2"
                >
                  <textarea
                    {...register('message', { required: 'Mesaj tələb olunur' })}
                    rows={5}
                    className={inputClass(Boolean(errors.message))}
                  />
                </Field>
              </div>

              <label className="mt-4 flex cursor-pointer items-start gap-2.5 text-sm">
                <input
                  type="checkbox"
                  {...register('privacyAccepted', { required: 'Razılıq tələb olunur' })}
                  className="mt-0.5 h-4 w-4 rounded accent-[var(--color-primary)]"
                />
                <span className="text-[var(--color-body)]">
                  Məlumatlarımın emalına razıyam
                </span>
              </label>
              {errors.privacyAccepted && (
                <p className="mt-1 text-xs text-[var(--color-danger)]">
                  {errors.privacyAccepted.message}
                </p>
              )}

              <button
                type="submit"
                disabled={submit.isPending}
                className="mt-5 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-60"
              >
                {submit.isPending ? (
                  <Loader2 size={16} className="animate-spin" aria-hidden="true" />
                ) : (
                  <Send size={15} aria-hidden="true" />
                )}
                Göndər
              </button>
            </form>
          )}
        </div>

        {/* ---------------- office info ---------------- */}
        {withInfo && showInfo && (
          <aside className="space-y-5">
            <div className="card p-5">
              <h3 className="mb-4 text-sm font-semibold uppercase tracking-wide text-[var(--color-heading)]">
                Əlaqə
              </h3>
              <ul className="space-y-3 text-sm">
                {company?.phones?.[0] && (
                  <li className="flex items-start gap-2.5">
                    <Phone size={16} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
                    <a
                      href={`tel:${company.phones[0].replace(/\s/g, '')}`}
                      className="text-[var(--color-body)] hover:text-[var(--color-primary)]"
                    >
                      {company.phones[0]}
                    </a>
                  </li>
                )}
                {company?.email && (
                  <li className="flex items-start gap-2.5">
                    <Mail size={16} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
                    <a
                      href={`mailto:${company.email}`}
                      className="break-all text-[var(--color-body)] hover:text-[var(--color-primary)]"
                    >
                      {company.email}
                    </a>
                  </li>
                )}
                {company?.address && (
                  <li className="flex items-start gap-2.5">
                    <MapPin size={16} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
                    <span className="text-[var(--color-body)]">{company.address}</span>
                  </li>
                )}
                {company?.workingHours && (
                  <li className="flex items-start gap-2.5">
                    <Clock size={16} className="mt-0.5 shrink-0 text-[var(--color-primary)]" aria-hidden="true" />
                    <span className="text-[var(--color-body)]">{company.workingHours}</span>
                  </li>
                )}
              </ul>
            </div>

            {showMap && company?.mapUrl && <MapEmbed url={company.mapUrl} />}
          </aside>
        )}
      </div>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// NEWSLETTER
// ---------------------------------------------------------------------------

export function NewsletterSection({ section }: SectionProps) {
  const inverted = isInverted(section.backgroundStyle);
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [subscribed, setSubscribed] = useState(false);

  const subscribe = useMutation({
    mutationFn: () => postVoid('/public/newsletter-subscriptions', { email }),
    onSuccess: () => {
      setSubscribed(true);
      setEmail('');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const isCentered = section.variant === 'CENTERED';

  return (
    <SectionShell section={section} showHeader={false} compact>
      <div
        className={clsx(
          isCentered
            ? 'mx-auto max-w-xl text-center'
            : 'flex flex-wrap items-center justify-between gap-6',
        )}
      >
        <div className={clsx(!isCentered && 'min-w-[260px] flex-1')}>
          {section.title && (
            <h2
              className={clsx(
                'text-xl font-bold tracking-tight lg:text-2xl',
                inverted && 'text-white',
              )}
            >
              {section.title}
            </h2>
          )}
          {section.subtitle && (
            <p
              className={clsx(
                'mt-1.5 text-sm',
                inverted ? 'text-white/80' : 'text-[var(--color-body)]',
              )}
            >
              {section.subtitle}
            </p>
          )}
        </div>

        {subscribed ? (
          <p
            className={clsx(
              'inline-flex items-center gap-2 text-sm font-medium',
              inverted ? 'text-white' : 'text-[var(--color-success)]',
              isCentered && 'mt-5 justify-center',
            )}
          >
            <Check size={16} aria-hidden="true" />
            Abunəliyiniz qeydə alındı — e-poçtunuzu yoxlayın.
          </p>
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (email.trim()) subscribe.mutate();
            }}
            className={clsx('flex w-full max-w-md gap-2', isCentered && 'mx-auto mt-5')}
          >
            <label htmlFor={`newsletter-${section.id}`} className="sr-only">
              E-poçt
            </label>
            <input
              id={`newsletter-${section.id}`}
              type="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="E-poçt ünvanınız"
              className={clsx(
                'min-w-0 flex-1 rounded-[var(--radius-input)] border px-4 py-2.5 text-sm outline-none transition-colors',
                inverted
                  ? 'border-white/25 bg-white/10 text-white placeholder:text-white/50 focus:border-white/50'
                  : 'border-[var(--color-border)] bg-[var(--color-canvas)] focus:border-[var(--color-primary)]',
              )}
            />
            <button
              type="submit"
              disabled={subscribe.isPending}
              className={clsx(
                'rounded-[var(--radius-button)] px-5 py-2.5 text-sm font-semibold transition-colors disabled:opacity-60',
                inverted
                  ? 'bg-white text-[var(--color-secondary)] hover:bg-white/90'
                  : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]',
              )}
            >
              {subscribe.isPending ? '…' : 'Abunə ol'}
            </button>
          </form>
        )}
      </div>
    </SectionShell>
  );
}

// ---------------------------------------------------------------------------
// MAP
// ---------------------------------------------------------------------------

export function MapSection({ section }: SectionProps) {
  const { company } = useSiteConfig();
  const url = configString(section, 'mapUrl') ?? company?.mapUrl ?? null;

  if (!url) {
    return null;
  }

  return (
    <SectionShell
      section={section}
      containerClassName={section.variant === 'BOXED' ? 'mx-auto max-w-4xl' : undefined}
    >
      <MapEmbed url={url} tall={section.variant === 'FULL_WIDTH'} />
    </SectionShell>
  );
}

/**
 * Map embed.
 *
 * The URL comes from admin configuration, so it is checked against an allow-list before
 * being placed in an iframe `src` — an arbitrary URL there is a framing vector.
 */
function MapEmbed({ url, tall }: { url: string; tall?: boolean }) {
  const allowed = /^https:\/\/(www\.)?(google\.[a-z.]+\/maps|maps\.google\.[a-z.]+|openstreetmap\.org|yandex\.[a-z.]+\/map)/i;

  if (!allowed.test(url)) {
    return null;
  }

  return (
    <div
      className={clsx(
        'overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)]',
        tall ? 'h-[420px]' : 'h-56',
      )}
    >
      <iframe
        src={url}
        title="Xəritə"
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        sandbox="allow-scripts allow-same-origin allow-popups"
        className="h-full w-full border-0"
      />
    </div>
  );
}

// ---------------------------------------------------------------------------

function Field({
  label,
  error,
  required,
  className,
  children,
}: {
  label: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={clsx('block', className)}>
      <span className="mb-1.5 block text-sm font-medium text-[var(--color-heading)]">
        {label}
        {required && <span className="ms-0.5 text-[var(--color-danger)]">*</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-[var(--color-danger)]">{error}</span>}
    </label>
  );
}

function inputClass(hasError: boolean): string {
  return clsx(
    'w-full rounded-[var(--radius-input)] border bg-[var(--color-canvas)] px-3 py-2.5 text-sm outline-none transition-colors',
    hasError
      ? 'border-[var(--color-danger)] focus:border-[var(--color-danger)]'
      : 'border-[var(--color-border)] focus:border-[var(--color-primary)]',
  );
}
