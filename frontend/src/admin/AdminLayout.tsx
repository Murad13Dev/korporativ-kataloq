import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import clsx from 'clsx';
import {
  Boxes,
  ChevronDown,
  ExternalLink,
  FileText,
  Folders,
  Image,
  Inbox,
  LayoutDashboard,
  LayoutPanelTop,
  LogOut,
  Menu,
  MessageCircle,
  Navigation,
  Newspaper,
  Package,
  Settings,
  ShoppingBag,
  SlidersHorizontal,
  Star,
  Tag,
  Ticket,
  Users,
  UsersRound,
  X,
} from 'lucide-react';
import { ROLE_LABELS, useAdminAuth, type Capability } from './auth/adminAuth';
import { hasCapability } from './auth/adminAuth';

interface NavEntry {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  capability?: Capability;
  end?: boolean;
}

interface NavGroup {
  label: string;
  entries: NavEntry[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Ümumi',
    entries: [{ to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true }],
  },
  {
    label: 'Kataloq',
    entries: [
      { to: '/admin/products', label: 'Məhsullar', icon: Package, capability: 'catalog.manage' },
      { to: '/admin/categories', label: 'Kateqoriyalar', icon: Folders, capability: 'catalog.manage' },
      { to: '/admin/attributes', label: 'Attributlar', icon: SlidersHorizontal, capability: 'catalog.manage' },
      { to: '/admin/discounts', label: 'Endirimlər', icon: Tag, capability: 'discounts.manage' },
      { to: '/admin/promo-codes', label: 'Promo kodlar', icon: Ticket, capability: 'discounts.manage' },
      { to: '/admin/reviews', label: 'Rəylər', icon: Star, capability: 'catalog.manage' },
      { to: '/admin/bundles', label: 'Bundle-lar', icon: Boxes, capability: 'catalog.manage' },
    ],
  },
  {
    label: 'Satış',
    entries: [
      { to: '/admin/orders', label: 'Sifarişlər', icon: ShoppingBag, capability: 'orders.manage' },
      { to: '/admin/customers', label: 'Müştərilər', icon: UsersRound, capability: 'customers.view' },
      { to: '/admin/chat', label: 'Canlı dəstək', icon: MessageCircle, capability: 'chat.handle' },
      { to: '/admin/contact-requests', label: 'Əlaqə sorğuları', icon: Inbox, capability: 'orders.manage' },
    ],
  },
  {
    label: 'Məzmun',
    entries: [
      { to: '/admin/pages', label: 'Səhifələr', icon: FileText, capability: 'content.manage' },
      { to: '/admin/posts', label: 'Bloq', icon: Newspaper, capability: 'content.manage' },
      { to: '/admin/navigation', label: 'Naviqasiya', icon: Navigation, capability: 'content.manage' },
      { to: '/admin/content', label: 'Kontent blokları', icon: LayoutPanelTop, capability: 'content.manage' },
      { to: '/admin/media', label: 'Media', icon: Image, capability: 'media.manage' },
    ],
  },
  {
    label: 'Sistem',
    entries: [
      { to: '/admin/settings', label: 'Parametrlər', icon: Settings, capability: 'settings.manage' },
      { to: '/admin/users', label: 'İstifadəçilər', icon: Users, capability: 'users.manage' },
    ],
  },
];

/**
 * Admin shell.
 *
 * The sidebar is permanent on desktop and a drawer below `lg`. Navigation items the
 * signed-in role cannot use are removed rather than disabled — a greyed-out menu full of
 * things you may never click is noise, and the API enforces the same rules anyway.
 */
export function AdminLayout() {
  const { user, logout } = useAdminAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    setSidebarOpen(false);
    setUserMenuOpen(false);
  }, [location.pathname]);

  const visibleGroups = NAV_GROUPS.map((group) => ({
    ...group,
    entries: group.entries.filter(
      (entry) => !entry.capability || hasCapability(user?.role, entry.capability),
    ),
  })).filter((group) => group.entries.length > 0);

  return (
    <div className="min-h-screen bg-[var(--color-muted)]">
      {/* ---------------- sidebar ---------------- */}
      <aside
        className={clsx(
          'fixed inset-y-0 start-0 z-50 w-64 border-e border-[var(--color-border)]',
          'bg-[var(--color-surface)] transition-transform duration-200 lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full rtl:translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-[var(--color-border)] px-5">
          <Link to="/admin" className="font-display text-base font-bold text-[var(--color-heading)]">
            Admin panel
          </Link>
          <button
            type="button"
            onClick={() => setSidebarOpen(false)}
            aria-label="Menyunu bağla"
            className="rounded p-1 text-[var(--color-subtle)] lg:hidden"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <nav
          className="flex h-[calc(100vh-4rem)] flex-col gap-5 overflow-y-auto px-3 py-4"
          aria-label="Admin menyusu"
        >
          {visibleGroups.map((group) => (
            <div key={group.label}>
              <p className="mb-1.5 px-3 text-[0.6875rem] font-semibold uppercase tracking-wider text-[var(--color-subtle)]">
                {group.label}
              </p>
              <ul className="space-y-0.5">
                {group.entries.map((entry) => {
                  const Icon = entry.icon;
                  return (
                    <li key={entry.to}>
                      <NavLink
                        to={entry.to}
                        end={entry.end}
                        className={({ isActive }) =>
                          clsx(
                            'flex items-center gap-3 rounded-[var(--radius-button)] px-3 py-2 text-sm font-medium transition-colors',
                            isActive
                              ? 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                              : 'text-[var(--color-body)] hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]',
                          )
                        }
                      >
                        <Icon size={17} aria-hidden="true" />
                        {entry.label}
                      </NavLink>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}

          <a
            href="/"
            target="_blank"
            rel="noopener noreferrer"
            className="mt-auto flex items-center gap-3 rounded-[var(--radius-button)] px-3 py-2 text-sm font-medium text-[var(--color-body)] hover:bg-[var(--color-muted)]"
          >
            <ExternalLink size={17} aria-hidden="true" />
            Saytı aç
          </a>
        </nav>
      </aside>

      {/* Backdrop for the mobile drawer. */}
      {sidebarOpen && (
        <button
          type="button"
          aria-label="Menyunu bağla"
          onClick={() => setSidebarOpen(false)}
          className="fixed inset-0 z-40 bg-black/40 lg:hidden"
        />
      )}

      {/* ---------------- main ---------------- */}
      <div className="lg:ps-64">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface)]/85 px-4 backdrop-blur-md lg:px-8">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            aria-label="Menyunu aç"
            className="rounded-[var(--radius-button)] p-2 text-[var(--color-body)] hover:bg-[var(--color-muted)] lg:hidden"
          >
            <Menu size={19} aria-hidden="true" />
          </button>

          <div className="relative ms-auto">
            <button
              type="button"
              onClick={() => setUserMenuOpen((open) => !open)}
              aria-expanded={userMenuOpen}
              className="flex items-center gap-2.5 rounded-[var(--radius-button)] px-2 py-1.5 transition-colors hover:bg-[var(--color-muted)]"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-primary)] text-xs font-bold text-[var(--color-primary-contrast)]">
                {user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}` : '?'}
              </span>
              <span className="hidden text-start sm:block">
                <span className="block text-sm font-medium leading-tight text-[var(--color-heading)]">
                  {user?.firstName} {user?.lastName}
                </span>
                <span className="block text-xs leading-tight text-[var(--color-subtle)]">
                  {user ? ROLE_LABELS[user.role] : ''}
                </span>
              </span>
              <ChevronDown size={15} className="text-[var(--color-subtle)]" aria-hidden="true" />
            </button>

            {userMenuOpen && (
              <div className="absolute end-0 top-full mt-1 w-56 overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-popover)]">
                <div className="border-b border-[var(--color-border)] px-4 py-3">
                  <p className="truncate text-sm font-medium text-[var(--color-heading)]">
                    {user?.email}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void logout()}
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-sm text-[var(--color-danger)] transition-colors hover:bg-[var(--color-muted)]"
                >
                  <LogOut size={15} aria-hidden="true" />
                  Çıxış
                </button>
              </div>
            )}
          </div>
        </header>

        <main className="px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
