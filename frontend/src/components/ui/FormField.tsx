import type { ReactNode } from 'react';
import clsx from 'clsx';

/** Labeled form field with an inline error slot. Shared across the account pages. */
export function Field({
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
  children: ReactNode;
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

export function inputClass(hasError: boolean): string {
  return clsx(
    'w-full rounded-[var(--radius-input)] border bg-[var(--color-canvas)] px-3 py-2.5 text-sm outline-none transition-colors',
    hasError
      ? 'border-[var(--color-danger)] focus:border-[var(--color-danger)]'
      : 'border-[var(--color-border)] focus:border-[var(--color-primary)]',
  );
}
