import { Link } from 'react-router-dom';
import { SearchX } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="container-site flex flex-col items-center py-24 text-center">
      <SearchX size={44} className="animate-float mb-5 text-[var(--color-subtle)]" aria-hidden="true" />
      <p className="mb-1 font-display text-5xl font-bold text-[var(--color-primary)]">404</p>
      <h1 className="mb-3 text-2xl font-bold">Səhifə tapılmadı</h1>
      <p className="mb-8 max-w-sm text-[var(--color-body)]">
        Axtardığınız səhifə mövcud deyil və ya köçürülüb.
      </p>
      <div className="flex flex-wrap justify-center gap-3">
        <Link
          to="/"
          className="rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-[var(--color-primary-contrast)]"
        >
          Ana səhifə
        </Link>
        <Link
          to="/kataloq"
          className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-6 py-3 text-sm font-semibold"
        >
          Kataloqa bax
        </Link>
      </div>
    </div>
  );
}
