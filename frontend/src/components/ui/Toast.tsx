import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import clsx from 'clsx';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';

type ToastVariant = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: string;
  variant: ToastVariant;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  warning: (message: string) => void;
  info: (message: string) => void;
  dismiss: (id: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const VARIANTS: Record<ToastVariant, { icon: typeof Info; className: string; duration: number }> = {
  success: {
    icon: CheckCircle2,
    className: 'border-[var(--color-success)] bg-[var(--color-success)]/10 text-[var(--color-success)]',
    duration: 3000,
  },
  // Errors stay longer: they usually carry an instruction the user must read.
  error: {
    icon: XCircle,
    className: 'border-[var(--color-danger)] bg-[var(--color-danger)]/10 text-[var(--color-danger)]',
    duration: 6000,
  },
  warning: {
    icon: AlertTriangle,
    className: 'border-[var(--color-warning)] bg-[var(--color-warning)]/10 text-[var(--color-warning)]',
    duration: 5000,
  },
  info: {
    icon: Info,
    className: 'border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
    duration: 4000,
  },
};

const MAX_VISIBLE = 4;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (variant: ToastVariant, message: string) => {
      const id = crypto.randomUUID();
      setToasts((current) => {
        // Identical consecutive messages (a repeated failing action) would otherwise
        // stack into a wall; collapse them instead.
        if (current.some((toast) => toast.message === message && toast.variant === variant)) {
          return current;
        }
        return [...current, { id, variant, message }].slice(-MAX_VISIBLE);
      });
      window.setTimeout(() => dismiss(id), VARIANTS[variant].duration);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push('success', message),
      error: (message) => push('error', message),
      warning: (message) => push('warning', message),
      info: (message) => push('info', message),
      dismiss,
    }),
    [push, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}

      {/*
        `polite` rather than `assertive`: a toast should not interrupt a screen-reader
        user mid-sentence for something as routine as "added to cart".
      */}
      <div
        role="region"
        aria-label="Bildirişlər"
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 end-4 z-[100] flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0"
      >
        {toasts.map((toast) => {
          const variant = VARIANTS[toast.variant];
          const Icon = variant.icon;
          return (
            <div
              key={toast.id}
              className={clsx(
                'pointer-events-auto flex items-start gap-3 rounded-[var(--radius-card)] border px-4 py-3',
                'bg-[var(--color-surface)] shadow-[var(--shadow-popover)]',
                'animate-[toast-in_240ms_var(--ease-out-quint)]',
                variant.className,
              )}
            >
              <Icon size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
              <p className="flex-1 text-sm font-medium text-[var(--color-heading)]">
                {toast.message}
              </p>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Bildirişi bağla"
                className="shrink-0 rounded p-0.5 text-[var(--color-subtle)] transition-colors hover:text-[var(--color-heading)]"
              >
                <X size={16} aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>

      <style>{`
        @keyframes toast-in {
          from { opacity: 0; transform: translateY(8px) scale(0.98); }
          to   { opacity: 1; transform: translateY(0) scale(1); }
        }
      `}</style>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a <ToastProvider>');
  }
  return context;
}
