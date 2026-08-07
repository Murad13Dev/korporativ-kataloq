import clsx from 'clsx';
import { formatPrice } from '@/lib/format';
import type { PriceInfo } from '@/types/api';

type Size = 'sm' | 'md' | 'lg' | 'xl';

const SIZE_CLASSES: Record<Size, { now: string; was: string; gap: string }> = {
  sm: { now: 'text-sm', was: 'text-xs', gap: 'gap-1.5' },
  md: { now: 'text-lg', was: 'text-sm', gap: 'gap-2' },
  lg: { now: 'text-2xl', was: 'text-base', gap: 'gap-2.5' },
  xl: { now: 'text-3xl sm:text-4xl', was: 'text-lg', gap: 'gap-3' },
};

/**
 * The price block.
 *
 * Three states, and the distinction between them is the whole point:
 *
 *  1. **Hidden** — `showPrice` is false: a B2B catalog that quotes on request.
 *  2. **Plain**  — no campaign: one number in the heading colour.
 *  3. **Discounted** — the sale price in the sale colour, the original struck through
 *     beside it, and the saving spelled out. Showing the old price *and* the amount
 *     saved is what makes a discount legible; a lone "-25%" makes the shopper do maths.
 *
 * The struck-through original is marked `aria-hidden` and replaced with a proper
 * screen-reader sentence, because "249 manat 199 manat" read aloud is meaningless.
 */
export function PriceDisplay({
  price,
  showPrice = true,
  size = 'md',
  showSaving = true,
  inquiryLabel = 'Qiymət üçün müraciət edin',
  className,
}: {
  price: PriceInfo;
  showPrice?: boolean;
  size?: Size;
  showSaving?: boolean;
  inquiryLabel?: string;
  className?: string;
}) {
  const sizes = SIZE_CLASSES[size];

  if (!showPrice || price.finalPrice === null) {
    return (
      <span className={clsx('font-medium text-[var(--color-subtle)]', sizes.was, className)}>
        {inquiryLabel}
      </span>
    );
  }

  if (!price.discounted) {
    return (
      <span className={clsx('price-plain', sizes.now, className)}>
        {formatPrice(price.finalPrice)}
      </span>
    );
  }

  return (
    <div className={clsx('flex flex-col', className)}>
      <div className={clsx('flex flex-wrap items-baseline', sizes.gap)}>
        <span className={clsx('price-now', sizes.now)}>{formatPrice(price.finalPrice)}</span>

        <span className={clsx('price-was', sizes.was)} aria-hidden="true">
          {formatPrice(price.listPrice)}
        </span>

        {/* What a screen reader actually hears. */}
        <span className="sr-only">
          Endirimli qiymət {formatPrice(price.finalPrice)}, əvvəlki qiymət{' '}
          {formatPrice(price.listPrice)}, {price.savingPercentage} faiz endirim.
        </span>
      </div>

      {showSaving && price.savingAmount > 0 && (
        <span className={clsx('mt-0.5 font-medium text-[var(--color-success)]', sizes.was)}>
          {formatPrice(price.savingAmount)} qənaət
        </span>
      )}
    </div>
  );
}

/**
 * Single-line variant for cart and order rows, where vertical space is tight.
 */
export function InlinePrice({
  unitPrice,
  originalUnitPrice,
  className,
}: {
  unitPrice: number;
  originalUnitPrice: number;
  className?: string;
}) {
  const discounted = originalUnitPrice > unitPrice;

  return (
    <span className={clsx('inline-flex items-baseline gap-1.5', className)}>
      <span className={discounted ? 'price-now text-base' : 'price-plain text-base'}>
        {formatPrice(unitPrice)}
      </span>
      {discounted && (
        <>
          <span className="price-was text-xs" aria-hidden="true">
            {formatPrice(originalUnitPrice)}
          </span>
          <span className="sr-only">
            əvvəlki qiymət {formatPrice(originalUnitPrice)}
          </span>
        </>
      )}
    </span>
  );
}
