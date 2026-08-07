import { useEffect } from 'react';
import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import clsx from 'clsx';
import { Award, Heart, LogOut, MapPin, Package, User } from 'lucide-react';
import { useCustomerAuth } from '@/hooks/useCustomerAuth';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { SectionLoader } from '@/components/ui/PageLoader';

const BASE_TABS = [
  { to: '/hesab/profil', label: 'Profil', icon: User },
  { to: '/hesab/unvanlar', label: 'Ünvanlar', icon: MapPin },
  { to: '/hesab/sifarisler', label: 'Sifarişlər', icon: Package },
  { to: '/hesab/xallar', label: 'Xallar', icon: Award },
];

const WISHLIST_TAB = { to: '/hesab/sevimliler', label: 'Sevimlilər', icon: Heart };

/**
 * Shell for the customer account area.
 *
 * Restoring the session happens here rather than in `StoreLayout`, so an anonymous
 * visitor browsing the catalog never pays for a `/customer/auth/refresh` round trip
 * they didn't ask for — only someone who opens `/hesab` does.
 */
export function AccountLayout() {
  const { customer, status, restore, logout } = useCustomerAuth();
  const { features } = useSiteConfig();
  const location = useLocation();

  const tabs = features.wishlistEnabled ? [...BASE_TABS, WISHLIST_TAB] : BASE_TABS;

  useEffect(() => {
    if (status === 'idle') {
      void restore();
    }
  }, [status, restore]);

  if (status === 'idle' || status === 'checking') {
    return <SectionLoader label="Hesab yoxlanılır…" />;
  }

  if (status === 'anonymous') {
    const next = encodeURIComponent(location.pathname);
    return <Navigate to={`/hesab/giris?next=${next}`} replace />;
  }

  return (
    <div className="container-site py-8 lg:py-12">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">Hesabım</h1>

      <div className="grid gap-8 lg:grid-cols-[220px_1fr]">
        <aside>
          <div className="card mb-4 p-4">
            <p className="truncate text-sm font-semibold text-[var(--color-heading)]">
              {customer?.firstName} {customer?.lastName}
            </p>
            <p className="truncate text-xs text-[var(--color-subtle)]">{customer?.email}</p>
          </div>

          <nav className="card space-y-0.5 p-2" aria-label="Hesab menyusu">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <NavLink
                  key={tab.to}
                  to={tab.to}
                  className={({ isActive }) =>
                    clsx(
                      'flex items-center gap-2.5 rounded-[var(--radius-button)] px-3 py-2.5 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                        : 'text-[var(--color-body)] hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]',
                    )
                  }
                >
                  <Icon size={17} aria-hidden="true" />
                  {tab.label}
                </NavLink>
              );
            })}

            <button
              type="button"
              onClick={() => void logout()}
              className="flex w-full items-center gap-2.5 rounded-[var(--radius-button)] px-3 py-2.5 text-sm font-medium text-[var(--color-danger)] transition-colors hover:bg-[var(--color-muted)]"
            >
              <LogOut size={17} aria-hidden="true" />
              Çıxış
            </button>
          </nav>
        </aside>

        <div className="min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
