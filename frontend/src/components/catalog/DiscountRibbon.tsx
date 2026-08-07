import clsx from 'clsx';
import { ribbonColor } from '@/lib/theme';
import type { PriceInfo } from '@/types/api';

/**
 * The diagonal corner ribbon on a product card.
 *
 * Design notes:
 * - The label text comes from the server (`badgeLabel`): "-25%" by default, or the
 *   campaign's own wording ("BLACK FRIDAY"). The client never computes a percentage,
 *   which keeps one rounding rule for the whole system.
 * - Colour comes from a *theme token* chosen by `badgeStyle`, so ribbons stay on brand
 *   for every client installation instead of being hard-coded red.
 * - `aria-hidden` because the saving is already announced by the price block below;
 *   a screen reader hearing "-25%" twice is noise.
 */
export function DiscountRibbon({
  price,
  className,
}: {
  price: PriceInfo;
  className?: string;
}) {
  if (!price.discounted || !price.showBadge || !price.badgeLabel) {
    return null;
  }

  return (
    <div className={clsx('ribbon', className)} aria-hidden="true">
      <span
        className="ribbon__label"
        style={{ '--ribbon-color': ribbonColor(price.badgeStyle, price.badgeColor) } as React.CSSProperties}
      >
        {price.badgeLabel}
      </span>
    </div>
  );
}

/**
 * Flat pill variant for dense rows (cart lines, order lines, admin tables) where a
 * diagonal ribbon would fight with the layout.
 */
export function DiscountPill({
  price,
  className,
}: {
  price: Pick<PriceInfo, 'discounted' | 'badgeLabel' | 'badgeStyle' | 'badgeColor'>;
  className?: string;
}) {
  if (!price.discounted || !price.badgeLabel) {
    return null;
  }

  return (
    <span
      className={clsx('discount-pill', className)}
      style={{ '--ribbon-color': ribbonColor(price.badgeStyle, price.badgeColor) } as React.CSSProperties}
    >
      {price.badgeLabel}
    </span>
  );
}

/**
 * Non-discount status flags ("Yeni", "Seçilmiş", "Son ədədlər"). Rendered on the
 * opposite corner from the ribbon so the two never overlap.
 */
export function StatusBadges({
  isNew,
  featured,
  lowStock,
  outOfStock,
}: {
  isNew?: boolean;
  featured?: boolean;
  lowStock?: boolean;
  outOfStock?: boolean;
}) {
  const badges: { label: string; className: string }[] = [];

  // Out of stock is the only state a shopper must act on, so it wins the slot.
  if (outOfStock) {
    badges.push({ label: 'Bitib', className: 'bg-[var(--color-subtle)] text-white' });
  } else {
    if (lowStock) {
      badges.push({ label: 'Son ədədlər', className: 'bg-[var(--color-warning)] text-white' });
    }
    if (isNew) {
      badges.push({ label: 'Yeni', className: 'bg-[var(--color-accent)] text-white' });
    }
    if (featured && !isNew && !lowStock) {
      badges.push({ label: 'Seçilmiş', className: 'bg-[var(--color-primary)] text-white' });
    }
  }

  if (badges.length === 0) {
    return null;
  }

  return (
    <div className="absolute end-3 top-3 z-10 flex flex-col items-end gap-1.5">
      {badges.map((badge) => (
        <span
          key={badge.label}
          className={clsx(
            'rounded-full px-2.5 py-1 text-[0.6875rem] font-semibold leading-none shadow-sm',
            badge.className,
          )}
        >
          {badge.label}
        </span>
      ))}
    </div>
  );
}
