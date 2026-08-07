import type { CommerceConfig } from '@/types/api';

/**
 * Presentation helpers. Everything money-related goes through here so the
 * currency symbol, its position and the decimal rules live in exactly one place —
 * a client switching from ₼ to $ changes configuration, not components.
 */

const DEFAULT_LOCALE = 'az-AZ';

let commerce: CommerceConfig = {
  currency: 'AZN',
  currencySymbol: '₼',
  currencyPosition: 'SUFFIX',
};

/** Called once when the site configuration loads. */
export function configureFormatting(config: CommerceConfig | undefined): void {
  if (config) {
    commerce = { ...commerce, ...config };
  }
}

/**
 * Formats an amount for display.
 *
 * Prices always show two decimals — "249" and "249.00" sitting next to each other in a
 * grid look like a rendering bug, and a struck-through original must align with the
 * price beneath it.
 */
export function formatPrice(
  amount: number | null | undefined,
  options: { withSymbol?: boolean; compact?: boolean } = {},
): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '—';
  }

  const { withSymbol = true, compact = false } = options;

  const formatted = new Intl.NumberFormat(DEFAULT_LOCALE, {
    minimumFractionDigits: compact && Number.isInteger(amount) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(amount);

  if (!withSymbol) {
    return formatted;
  }

  const symbol = commerce.currencySymbol || commerce.currency;
  return commerce.currencyPosition === 'PREFIX'
    ? `${symbol}${formatted}`
    : `${formatted} ${symbol}`;
}

export function currencySymbol(): string {
  return commerce.currencySymbol || commerce.currency;
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return new Intl.NumberFormat(DEFAULT_LOCALE).format(value);
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return `${Math.round(value)}%`;
}

// ---------------------------------------------------------------------------
// Dates
// ---------------------------------------------------------------------------

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(DEFAULT_LOCALE, {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(DEFAULT_LOCALE, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

/** "2 saat əvvəl", "dünən", "3 gün əvvəl". */
export function formatRelative(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const then = new Date(iso).getTime();
  const diffSeconds = Math.round((then - Date.now()) / 1000);
  const absolute = Math.abs(diffSeconds);

  const relative = new Intl.RelativeTimeFormat(DEFAULT_LOCALE, { numeric: 'auto' });

  if (absolute < 60) return relative.format(Math.round(diffSeconds), 'second');
  if (absolute < 3600) return relative.format(Math.round(diffSeconds / 60), 'minute');
  if (absolute < 86400) return relative.format(Math.round(diffSeconds / 3600), 'hour');
  if (absolute < 2592000) return relative.format(Math.round(diffSeconds / 86400), 'day');
  if (absolute < 31536000) return relative.format(Math.round(diffSeconds / 2592000), 'month');
  return relative.format(Math.round(diffSeconds / 31536000), 'year');
}

// ---------------------------------------------------------------------------
// Countdown
// ---------------------------------------------------------------------------

export interface Countdown {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  totalMs: number;
  expired: boolean;
}

export function countdownTo(iso: string | null | undefined): Countdown {
  const empty: Countdown = { days: 0, hours: 0, minutes: 0, seconds: 0, totalMs: 0, expired: true };
  if (!iso) {
    return empty;
  }

  const totalMs = new Date(iso).getTime() - Date.now();
  if (totalMs <= 0) {
    return empty;
  }

  const totalSeconds = Math.floor(totalMs / 1000);
  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
    totalMs,
    expired: false,
  };
}

/** Compact countdown wording: "4g 12s", "12s 30d", "45d 12san". */
export function formatCountdown(countdown: Countdown): string {
  if (countdown.expired) {
    return 'Bitdi';
  }
  if (countdown.days > 0) {
    return `${countdown.days}g ${countdown.hours}s`;
  }
  if (countdown.hours > 0) {
    return `${countdown.hours}s ${countdown.minutes}d`;
  }
  if (countdown.minutes > 0) {
    return `${countdown.minutes}d ${countdown.seconds}san`;
  }
  return `${countdown.seconds}san`;
}

// ---------------------------------------------------------------------------
// Misc
// ---------------------------------------------------------------------------

export function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, exponent);
  return `${value.toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

export function truncate(text: string | null | undefined, max: number): string {
  if (!text) {
    return '';
  }
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/** "Səbətdə 1 məhsul" / "Səbətdə 3 məhsul" — Azerbaijani has no plural suffix here. */
export function pluralize(count: number, word: string): string {
  return `${formatNumber(count)} ${word}`;
}
