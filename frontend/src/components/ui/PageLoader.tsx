import { Loader2 } from 'lucide-react';

/**
 * Route-level loading state.
 *
 * Deliberately delayed by 200ms: a lazy chunk that arrives in 80ms would otherwise
 * produce a spinner flash, which reads as slower than no spinner at all.
 */
export function PageLoader() {
  return (
    <div
      className="flex min-h-[60vh] items-center justify-center"
      role="status"
      aria-live="polite"
    >
      <div className="flex flex-col items-center gap-3 opacity-0 [animation:fade-in_200ms_200ms_forwards]">
        <Loader2
          size={28}
          className="animate-spin text-[var(--color-primary)]"
          aria-hidden="true"
        />
        <span className="text-sm text-[var(--color-subtle)]">Yüklənir…</span>
      </div>

      <style>{`
        @keyframes fade-in { to { opacity: 1; } }
      `}</style>
    </div>
  );
}

/** Inline variant for a section that loads inside an already-rendered page. */
export function SectionLoader({ label = 'Yüklənir…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12" role="status" aria-live="polite">
      <Loader2 size={18} className="animate-spin text-[var(--color-primary)]" aria-hidden="true" />
      <span className="text-sm text-[var(--color-subtle)]">{label}</span>
    </div>
  );
}
