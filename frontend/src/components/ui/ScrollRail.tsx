import { useEffect, useRef, useState, type ReactNode } from 'react';
import clsx from 'clsx';
import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * A horizontally-scrolling rail with prev/next buttons.
 *
 * `overflow-x: auto` alone lets a mouse-and-keyboard user scroll sideways only via
 * shift+wheel or a trackpad swipe — neither is discoverable, so on a plain scroll
 * container most desktop visitors never find out the rail moves at all. The arrow
 * buttons make it obvious, and hide themselves at whichever edge there is nothing
 * left to scroll to.
 */
export function ScrollRail({
  children,
  className,
  inverted = false,
}: {
  children: ReactNode;
  className?: string;
  inverted?: boolean;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateEdges = () => {
    const el = trackRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  };

  useEffect(() => {
    updateEdges();
    const el = trackRef.current;
    if (!el) return;
    const onScroll = () => updateEdges();
    el.addEventListener('scroll', onScroll, { passive: true });
    const observer = new ResizeObserver(updateEdges);
    observer.observe(el);
    return () => {
      el.removeEventListener('scroll', onScroll);
      observer.disconnect();
    };
  }, [children]);

  const scrollBy = (direction: 1 | -1) => {
    const el = trackRef.current;
    if (!el) return;
    el.scrollBy({ left: direction * el.clientWidth * 0.85, behavior: 'smooth' });
  };

  const buttonClass = clsx(
    'absolute top-1/2 z-10 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full',
    'shadow-[var(--shadow-card)] transition-all duration-200',
    'hidden sm:flex',
    inverted
      ? 'border border-white/20 bg-white/10 text-white backdrop-blur hover:bg-white/20'
      : 'border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-heading)] hover:bg-[var(--color-muted)]',
  );

  return (
    <div className="relative">
      <div ref={trackRef} className={clsx('scroll-rail', className)}>
        {children}
      </div>

      {canScrollLeft && (
        <button
          type="button"
          aria-label="Əvvəlki"
          onClick={() => scrollBy(-1)}
          className={clsx(buttonClass, '-left-3 lg:-left-5')}
        >
          <ChevronLeft size={18} aria-hidden="true" />
        </button>
      )}
      {canScrollRight && (
        <button
          type="button"
          aria-label="Növbəti"
          onClick={() => scrollBy(1)}
          className={clsx(buttonClass, '-right-3 lg:-right-5')}
        >
          <ChevronRight size={18} aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
