import { useEffect, useState } from 'react';
import clsx from 'clsx';
import { Timer } from 'lucide-react';
import { countdownTo, formatCountdown, type Countdown } from '@/lib/format';

/**
 * Countdown to a campaign's end.
 *
 * Ticks every second regardless of how much time remains. The "blocks" variant always
 * displays a seconds digit once any larger unit is non-zero, so throttling the tick to
 * once a minute made that digit visibly freeze for up to 59 seconds — indistinguishable
 * from a broken timer that needed a page refresh to move.
 */
export function CountdownTimer({
  endsAt,
  variant = 'compact',
  className,
  onExpire,
}: {
  endsAt: string | null;
  variant?: 'compact' | 'blocks';
  className?: string;
  onExpire?: () => void;
}) {
  const [countdown, setCountdown] = useState<Countdown>(() => countdownTo(endsAt));

  useEffect(() => {
    if (!endsAt) {
      return;
    }

    let cancelled = false;

    const tick = () => {
      const next = countdownTo(endsAt);
      if (cancelled) {
        return;
      }
      setCountdown(next);
      if (next.expired) {
        onExpire?.();
      }
    };

    tick();

    const timer = window.setInterval(tick, 1_000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [endsAt, onExpire]);

  if (!endsAt || countdown.expired) {
    return null;
  }

  if (variant === 'compact') {
    return (
      <span
        className={clsx(
          'inline-flex items-center gap-1 text-xs font-medium tabular text-[var(--color-sale)]',
          className,
        )}
      >
        <Timer size={13} strokeWidth={2.5} aria-hidden="true" />
        <span>{formatCountdown(countdown)} qalıb</span>
      </span>
    );
  }

  const blocks = [
    { value: countdown.days, label: 'gün' },
    { value: countdown.hours, label: 'saat' },
    { value: countdown.minutes, label: 'dəq' },
    { value: countdown.seconds, label: 'san' },
  ];
  // A "0 gün" block is noise; drop leading zero units but always keep the last two.
  const firstMeaningful = blocks.findIndex((block) => block.value > 0);
  const visible = blocks.slice(Math.min(firstMeaningful === -1 ? 2 : firstMeaningful, 2));

  return (
    <div className={clsx('flex items-center gap-2', className)}>
      {visible.map((block, index) => (
        <div key={block.label} className="flex items-center gap-2">
          <div className="flex min-w-[3rem] flex-col items-center rounded-[var(--radius-button)] bg-[var(--color-sale)] px-2 py-1.5 text-white">
            <span className="text-lg font-bold leading-none tabular">
              {String(block.value).padStart(2, '0')}
            </span>
            <span className="mt-0.5 text-[0.625rem] uppercase tracking-wide opacity-90">
              {block.label}
            </span>
          </div>
          {index < visible.length - 1 && (
            <span className="text-lg font-bold text-[var(--color-sale)]" aria-hidden="true">
              :
            </span>
          )}
        </div>
      ))}
      <span className="sr-only">
        Kampaniyanın bitməsinə {formatCountdown(countdown)} qalıb
      </span>
    </div>
  );
}
