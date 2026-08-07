import { Component, type ErrorInfo, type ReactNode } from 'react';
import { RefreshCw, TriangleAlert } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defence for render-time crashes.
 *
 * Shows the user a way forward rather than a blank page. The technical detail is kept
 * behind a `<details>` and only in development — a shopper has no use for a stack trace,
 * and in production it can leak internals.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Replace with a real error reporter when one is wired up.
    console.error('Unhandled render error', error, info.componentStack);
  }

  private handleReload = () => {
    window.location.reload();
  };

  render() {
    const { error } = this.state;

    if (!error) {
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback;
    }

    return (
      <div className="flex min-h-[60vh] items-center justify-center px-4">
        <div className="max-w-md text-center">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-danger)]/10">
            <TriangleAlert size={26} className="text-[var(--color-danger)]" aria-hidden="true" />
          </div>

          <h1 className="mb-2 text-xl font-semibold">Nəsə səhv getdi</h1>
          <p className="mb-6 text-sm text-[var(--color-body)]">
            Gözlənilməz xəta baş verdi. Səhifəni yeniləyin və ya bir az sonra yenidən cəhd edin.
          </p>

          <div className="flex flex-wrap justify-center gap-3">
            <button
              type="button"
              onClick={this.handleReload}
              className="inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)]"
            >
              <RefreshCw size={16} aria-hidden="true" />
              Səhifəni yenilə
            </button>
            <a
              href="/"
              className="rounded-[var(--radius-button)] border border-[var(--color-border)] px-5 py-2.5 text-sm font-semibold text-[var(--color-heading)] transition-colors hover:bg-[var(--color-muted)]"
            >
              Ana səhifə
            </a>
          </div>

          {import.meta.env.DEV && (
            <details className="mt-8 text-start">
              <summary className="cursor-pointer text-xs text-[var(--color-subtle)]">
                Texniki detallar (yalnız development)
              </summary>
              <pre className="mt-2 max-h-64 overflow-auto rounded-[var(--radius-card)] bg-[var(--color-muted)] p-3 text-[0.6875rem] leading-relaxed">
                {error.stack ?? error.message}
              </pre>
            </details>
          )}
        </div>
      </div>
    );
  }
}
