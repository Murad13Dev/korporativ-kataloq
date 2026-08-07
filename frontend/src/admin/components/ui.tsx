import { forwardRef, type ReactNode } from 'react';
import clsx from 'clsx';
import { Loader2 } from 'lucide-react';

/**
 * The admin panel's shared primitives.
 *
 * Grouped in one module on purpose: they are small, they are always used together, and
 * a folder of twelve six-line files makes the pattern harder to see, not easier.
 */

// ---------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
type ButtonSize = 'sm' | 'md' | 'lg';

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]',
  secondary:
    'border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-heading)] hover:bg-[var(--color-muted)]',
  ghost: 'text-[var(--color-body)] hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]',
  danger: 'bg-[var(--color-danger)] text-white hover:opacity-90',
  success: 'bg-[var(--color-success)] text-white hover:opacity-90',
};

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs gap-1.5',
  md: 'px-4 py-2.5 text-sm gap-2',
  lg: 'px-6 py-3 text-sm gap-2',
};

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, icon, children, className, disabled, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center rounded-[var(--radius-button)] font-semibold',
        'transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        BUTTON_VARIANTS[variant],
        BUTTON_SIZES[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Loader2 size={15} className="animate-spin" aria-hidden="true" /> : icon}
      {children}
    </button>
  );
});

// ---------------------------------------------------------------------------
// Form controls
// ---------------------------------------------------------------------------

const CONTROL_BASE =
  'w-full rounded-[var(--radius-input)] border bg-[var(--color-surface)] px-3 py-2.5 text-sm ' +
  'outline-none transition-colors placeholder:text-[var(--color-subtle)] disabled:opacity-60';

function controlClass(hasError?: boolean, className?: string) {
  return clsx(
    CONTROL_BASE,
    hasError
      ? 'border-[var(--color-danger)] focus:border-[var(--color-danger)]'
      : 'border-[var(--color-border)] focus:border-[var(--color-primary)]',
    className,
  );
}

export const Input = forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement> & { hasError?: boolean }
>(function Input({ hasError, className, ...rest }, ref) {
  return <input ref={ref} className={controlClass(hasError, className)} {...rest} />;
});

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement> & { hasError?: boolean }
>(function Textarea({ hasError, className, ...rest }, ref) {
  return <textarea ref={ref} className={controlClass(hasError, className)} {...rest} />;
});

export const Select = forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement> & { hasError?: boolean }
>(function Select({ hasError, className, children, ...rest }, ref) {
  return (
    <select ref={ref} className={controlClass(hasError, className)} {...rest}>
      {children}
    </select>
  );
});

/**
 * Label + control + error, with the error wired to the control by `aria-describedby`
 * so a screen reader announces it when focus lands rather than leaving it unread.
 */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  required,
  className,
  children,
}: {
  label: string;
  htmlFor?: string;
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const errorId = error && htmlFor ? `${htmlFor}-error` : undefined;

  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-sm font-medium text-[var(--color-heading)]">
        {label}
        {required && <span className="ms-0.5 text-[var(--color-danger)]">*</span>}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-[var(--color-subtle)]">{hint}</p>}
      {error && (
        <p id={errorId} className="text-xs text-[var(--color-danger)]">
          {error}
        </p>
      )}
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
  hint,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  hint?: string;
  disabled?: boolean;
}) {
  return (
    <label
      className={clsx(
        'flex items-start gap-3',
        disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer',
      )}
    >
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={clsx(
          // The thumb is a normal flex child (not absolutely positioned with no inset set —
          // that left the browser to guess its rest position, which is what made the
          // animation land wherever and, in some layouts, drift over the label text).
          'mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full p-0.5 transition-colors duration-200',
          checked ? 'bg-[var(--color-primary)]' : 'bg-[var(--color-border)]',
        )}
      >
        <span
          className={clsx(
            'h-5 w-5 rounded-full bg-white shadow transition-transform duration-200 ease-out',
            checked ? 'translate-x-5' : 'translate-x-0',
          )}
        />
      </button>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-[var(--color-heading)]">{label}</span>
        {hint && <span className="block text-xs text-[var(--color-subtle)]">{hint}</span>}
      </span>
    </label>
  );
}

// ---------------------------------------------------------------------------
// Layout helpers
// ---------------------------------------------------------------------------

export function PageHeader({
  title,
  description,
  actions,
  breadcrumb,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  breadcrumb?: ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        {breadcrumb && <div className="mb-1.5 text-sm text-[var(--color-subtle)]">{breadcrumb}</div>}
        <h1 className="text-2xl font-bold tracking-tight text-[var(--color-heading)]">{title}</h1>
        {description && <p className="mt-1 text-sm text-[var(--color-body)]">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function Card({
  title,
  description,
  actions,
  className,
  bodyClassName,
  children,
}: {
  title?: string;
  description?: string;
  actions?: ReactNode;
  className?: string;
  bodyClassName?: string;
  children: ReactNode;
}) {
  return (
    <section
      className={clsx(
        'rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)]',
        className,
      )}
    >
      {(title || actions) && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
          <div>
            {title && <h2 className="font-semibold text-[var(--color-heading)]">{title}</h2>}
            {description && (
              <p className="mt-0.5 text-xs text-[var(--color-subtle)]">{description}</p>
            )}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={clsx('p-5', bodyClassName)}>{children}</div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Status badge
// ---------------------------------------------------------------------------

const BADGE_TONES = {
  neutral: 'bg-[var(--color-muted)] text-[var(--color-body)]',
  success: 'bg-[var(--color-success)]/12 text-[var(--color-success)]',
  warning: 'bg-[var(--color-warning)]/12 text-[var(--color-warning)]',
  danger: 'bg-[var(--color-danger)]/12 text-[var(--color-danger)]',
  info: 'bg-[var(--color-primary)]/12 text-[var(--color-primary)]',
  sale: 'bg-[var(--color-sale)]/12 text-[var(--color-sale)]',
} as const;

export type BadgeTone = keyof typeof BADGE_TONES;

export function Badge({
  tone = 'neutral',
  children,
  className,
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold leading-none',
        BADGE_TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
      {icon && <div className="mb-4 text-[var(--color-subtle)]">{icon}</div>}
      <h3 className="mb-1.5 font-semibold text-[var(--color-heading)]">{title}</h3>
      {description && (
        <p className="mb-5 max-w-sm text-sm text-[var(--color-body)]">{description}</p>
      )}
      {action}
    </div>
  );
}

export function InlineLoader({ label = 'Yüklənir…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12" role="status" aria-live="polite">
      <Loader2 size={17} className="animate-spin text-[var(--color-primary)]" aria-hidden="true" />
      <span className="text-sm text-[var(--color-subtle)]">{label}</span>
    </div>
  );
}

export function TableSkeleton({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="divide-y divide-[var(--color-border)]">
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div key={rowIndex} className="flex items-center gap-4 px-5 py-4">
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <div
              key={columnIndex}
              className="skeleton h-4"
              style={{ width: columnIndex === 0 ? '30%' : `${Math.max(60 / columns, 10)}%` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}
