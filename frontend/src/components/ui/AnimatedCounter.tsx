import { useEffect, useRef, useState } from 'react';
import { animate, useInView, useReducedMotion } from 'framer-motion';

/**
 * Count-up number for stat sections.
 *
 * The admin enters `value` as free text ("10,000+", "99.9", "4.8") because a stat is
 * whatever the client typed into a CMS field, not a typed number column — so this reads
 * out the numeric portion, animates that, and reassembles it with whatever prefix/suffix
 * characters surrounded it (the "+", the "%", the thousands separator). A value with no
 * digits at all (a stat that's actually just a word) renders as plain, static text.
 */
export function AnimatedCounter({ value, className }: { value: string; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, amount: 0.6 });
  const reduceMotion = useReducedMotion();
  const [display, setDisplay] = useState(value);

  const match = value.match(/^([^\d]*)([\d.,]*\d)([^\d]*)$/);

  useEffect(() => {
    if (!match || !inView) {
      return;
    }
    const [, prefix, numeric, suffix] = match;
    const decimals = numeric!.includes('.') ? numeric!.split('.')[1]!.length : 0;
    const hasThousands = numeric!.includes(',');
    const target = Number(numeric!.replace(/,/g, ''));

    if (!Number.isFinite(target)) {
      return;
    }

    if (reduceMotion) {
      setDisplay(value);
      return;
    }

    const controls = animate(0, target, {
      duration: 1.6,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (latest) => {
        const rounded = decimals > 0 ? latest.toFixed(decimals) : Math.round(latest).toString();
        const formatted = hasThousands
          ? Number(rounded).toLocaleString('en-US', {
              minimumFractionDigits: decimals,
              maximumFractionDigits: decimals,
            })
          : rounded;
        setDisplay(`${prefix}${formatted}${suffix}`);
      },
    });

    return () => controls.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inView, value]);

  return (
    <span ref={ref} className={className}>
      {match ? display : value}
    </span>
  );
}
