import { Link, useLocation } from 'react-router-dom';
import { Scale, X } from 'lucide-react';
import { useCompareStore } from '@/hooks/useCompare';

/**
 * Floating bar for the product comparison shortlist. Lives in the layout (not the
 * catalog page) so a shopper who adds items on one page and keeps browsing doesn't
 * lose the shortlist, and hides itself on the compare page — seeing "compare" while
 * already comparing is noise, not a shortcut.
 */
export function CompareBar() {
  const slugs = useCompareStore((state) => state.slugs);
  const clear = useCompareStore((state) => state.clear);
  const location = useLocation();

  if (slugs.length === 0 || location.pathname === '/muqayise') {
    return null;
  }

  return (
    <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--color-border)] bg-[var(--color-canvas)]/95 shadow-[var(--shadow-popover)] backdrop-blur-md">
      <div className="container-site flex flex-wrap items-center justify-between gap-3 py-3">
        <span className="flex items-center gap-2 text-sm font-medium text-[var(--color-heading)]">
          <Scale size={17} className="text-[var(--color-primary)]" aria-hidden="true" />
          {slugs.length} məhsul müqayisə üçün seçildi
        </span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={clear}
            className="flex items-center gap-1 rounded-[var(--radius-button)] px-3 py-2 text-sm font-medium text-[var(--color-subtle)] transition-colors hover:bg-[var(--color-muted)]"
          >
            <X size={14} aria-hidden="true" />
            Təmizlə
          </button>
          <Link
            to="/muqayise"
            className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
          >
            Müqayisə et
          </Link>
        </div>
      </div>
    </div>
  );
}
