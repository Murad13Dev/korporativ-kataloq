import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Menu, Phone, Search, ShoppingCart, User, X } from 'lucide-react';
import { useSiteConfig } from '@/hooks/useSiteConfig';
import { useCartCount } from '@/hooks/useCart';
import { ChatWidget } from '@/components/chat/ChatWidget';
import { CompareBar } from '@/components/catalog/CompareBar';
import { SearchOverlay } from '@/components/layout/SearchOverlay';
import { getData } from '@/lib/api';
import { setJsonLd } from '@/lib/theme';
import type { MenuItem } from '@/types/api';

/**
 * Fallback navigation, used only before the menus load or if none are published.
 * A header with no links at all reads as a broken site, so these stand in.
 */
const FALLBACK_LINKS: MenuItem[] = [
  { id: -1, title: 'Ana səhifə', url: '/', linkType: 'PAGE', icon: null, description: null, target: '_self', highlight: false, children: [] },
  { id: -2, title: 'Kataloq', url: '/kataloq', linkType: 'PAGE', icon: null, description: null, target: '_self', highlight: false, children: [] },
  { id: -3, title: 'Əlaqə', url: '/elaqe', linkType: 'PAGE', icon: null, description: null, target: '_self', highlight: true, children: [] },
];

/** Header and footer menus arrive in one call, so first paint needs no extra round trips. */
function useNavigation() {
  const query = useQuery({
    queryKey: ['navigation'],
    queryFn: () => getData<Record<string, MenuItem[]>>('/public/navigation'),
    staleTime: 15 * 60 * 1000,
  });

  const header = query.data?.HEADER ?? [];
  return {
    header: header.length > 0 ? header : FALLBACK_LINKS,
    footerPrimary: query.data?.FOOTER_PRIMARY ?? [],
    footerSecondary: query.data?.FOOTER_SECONDARY ?? [],
    legal: query.data?.LEGAL ?? [],
  };
}

/**
 * Shell for every storefront page: header, footer and the skip link.
 *
 * The header is sticky and translucent. Content scrolling under a solid bar loses the
 * connection between page and chrome; a blurred surface keeps the catalog visible
 * behind it while staying legible.
 */
export function StoreLayout() {
  const { company, branding, features, legal, isLoading } = useSiteConfig();
  const navigation = useNavigation();
  const cartCount = useCartCount(features.cartEnabled);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const location = useLocation();

  // Navigating with the mobile menu or search open would otherwise leave it covering the new page.
  useEffect(() => {
    setMenuOpen(false);
    setSearchOpen(false);
  }, [location.pathname]);

  // The scroll lock must be released even if the component unmounts while open.
  useEffect(() => {
    document.body.style.overflow = menuOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [menuOpen]);

  const companyName = company?.name ?? (isLoading ? '' : 'Kataloq');

  // Site-wide identity for search engines — one Organization/WebSite pair, live for the
  // whole storefront session, independent of whatever a single route adds on top of it.
  useEffect(() => {
    if (!company?.name) {
      return;
    }
    const origin = window.location.origin;
    return setJsonLd('organization', [
      {
        '@context': 'https://schema.org',
        '@type': 'Organization',
        name: company.name,
        url: origin,
        logo: branding?.logoLightUrl ?? undefined,
        email: company.email ?? undefined,
        telephone: company.phones?.[0] ?? undefined,
        address: company.address ?? undefined,
      },
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: company.name,
        url: origin,
        potentialAction: {
          '@type': 'SearchAction',
          target: `${origin}/kataloq?search={search_term_string}`,
          'query-input': 'required name=search_term_string',
        },
      },
    ]);
  }, [company, branding]);

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only-focusable absolute start-4 top-4 z-[200] rounded-[var(--radius-button)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-[var(--color-primary-contrast)]"
      >
        Əsas məzmuna keç
      </a>

      {/* ---------------- header ---------------- */}
      <header className="sticky top-0 z-50 border-b border-[var(--color-border)] bg-[var(--color-canvas)]/85 backdrop-blur-md">
        <div className="container-site flex h-[var(--header-height)] items-center gap-4">
          <Link to="/" className="flex shrink-0 items-center gap-2.5" aria-label={companyName}>
            {branding?.logoLightUrl ? (
              <img
                src={branding.logoLightUrl}
                alt={companyName}
                className="h-9 w-auto max-w-[180px] object-contain"
              />
            ) : (
              <span className="font-display text-lg font-bold tracking-tight text-[var(--color-heading)]">
                {companyName}
              </span>
            )}
          </Link>

          <nav className="ms-6 hidden items-center gap-1 lg:flex" aria-label="Əsas menyu">
            {navigation.header.map((item) => (
              <HeaderLink key={item.id} item={item} />
            ))}
          </nav>

          <div className="ms-auto flex items-center gap-1">
            {features.showSearch && (
              <button
                type="button"
                onClick={() => setSearchOpen((open) => !open)}
                aria-label={searchOpen ? 'Axtarışı bağla' : 'Axtarış'}
                aria-expanded={searchOpen}
                className="rounded-[var(--radius-button)] p-2.5 text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)]"
              >
                {searchOpen ? <X size={20} aria-hidden="true" /> : <Search size={20} aria-hidden="true" />}
              </button>
            )}

            <Link
              to="/hesab"
              aria-label="Hesab"
              className="hidden rounded-[var(--radius-button)] p-2.5 text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] sm:block"
            >
              <User size={20} aria-hidden="true" />
            </Link>

            {features.cartEnabled && (
              <Link
                to="/sebet"
                className="relative rounded-[var(--radius-button)] p-2.5 text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)]"
                aria-label={`Səbət${cartCount > 0 ? `, ${cartCount} məhsul` : ', boş'}`}
              >
                <ShoppingCart size={20} aria-hidden="true" />
                {cartCount > 0 && (
                  <span className="absolute -end-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-[var(--color-sale)] px-1 text-[0.6875rem] font-bold leading-none text-white tabular">
                    {cartCount > 99 ? '99+' : cartCount}
                  </span>
                )}
              </Link>
            )}

            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={menuOpen ? 'Menyunu bağla' : 'Menyunu aç'}
              aria-expanded={menuOpen}
              className="rounded-[var(--radius-button)] p-2.5 text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] lg:hidden"
            >
              {menuOpen ? <X size={20} aria-hidden="true" /> : <Menu size={20} aria-hidden="true" />}
            </button>
          </div>
        </div>

        {/* ---------------- mobile menu ---------------- */}
        {menuOpen && (
          <nav
            className="border-t border-[var(--color-border)] bg-[var(--color-canvas)] lg:hidden"
            aria-label="Mobil menyu"
          >
            <div className="container-site flex flex-col py-2">
              {navigation.header.map((item) => (
                <div key={item.id}>
                  <NavLink
                    to={item.url}
                    end={item.url === '/'}
                    className={({ isActive }) =>
                      clsx(
                        'block rounded-[var(--radius-button)] px-3 py-3 text-base font-medium transition-colors',
                        isActive
                          ? 'bg-[var(--color-muted)] text-[var(--color-primary)]'
                          : 'text-[var(--color-body)]',
                      )
                    }
                  >
                    {item.title}
                  </NavLink>
                  {/* Sub-items are flattened one level in: a nested drawer on a phone
                      hides more than it reveals. */}
                  {item.children.map((child) => (
                    <NavLink
                      key={child.id}
                      to={child.url}
                      className="block rounded-[var(--radius-button)] px-3 py-2.5 ps-7 text-sm text-[var(--color-body)]"
                    >
                      {child.title}
                    </NavLink>
                  ))}
                </div>
              ))}
              <NavLink
                to="/hesab"
                className="rounded-[var(--radius-button)] px-3 py-3 text-base font-medium text-[var(--color-body)]"
              >
                Hesabım
              </NavLink>
            </div>
          </nav>
        )}

        {searchOpen && <SearchOverlay onClose={() => setSearchOpen(false)} />}
      </header>

      {/* ---------------- content ---------------- */}
      <main id="main" className="flex-1">
        <Outlet />
      </main>

      {/* ---------------- footer ---------------- */}
      <footer className="mt-20 bg-[var(--color-footer)] text-[var(--color-footer-text)]">
        <div className="container-site py-14">
          <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
            <div className="lg:col-span-2">
              <span className="font-display text-lg font-bold text-white">{companyName}</span>
              {company?.shortDescription && (
                <p className="mt-3 max-w-sm text-sm leading-relaxed">{company.shortDescription}</p>
              )}
              {company?.phones?.[0] && (
                <a
                  href={`tel:${company.phones[0].replace(/\s/g, '')}`}
                  className="mt-5 inline-flex items-center gap-2 text-sm font-medium text-white transition-opacity hover:opacity-80"
                >
                  <Phone size={16} aria-hidden="true" />
                  {company.phones[0]}
                </a>
              )}
            </div>

            <nav aria-label="Alt menyu">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-white">
                Menyu
              </h2>
              <ul className="space-y-2 text-sm">
                {(navigation.footerPrimary.length > 0
                  ? navigation.footerPrimary
                  : navigation.header
                ).map((item) => (
                  <li key={item.id}>
                    <Link to={item.url} className="transition-colors hover:text-white">
                      {item.title}
                    </Link>
                  </li>
                ))}
                <li>
                  <Link to="/sifarisi-izle" className="transition-colors hover:text-white">
                    Sifarişi izlə
                  </Link>
                </li>
              </ul>
            </nav>

            <div>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-white">
                Əlaqə
              </h2>
              <ul className="space-y-2 text-sm">
                {company?.email && (
                  <li>
                    <a href={`mailto:${company.email}`} className="transition-colors hover:text-white">
                      {company.email}
                    </a>
                  </li>
                )}
                {company?.address && <li>{company.address}</li>}
                {company?.workingHours && <li>{company.workingHours}</li>}
              </ul>
            </div>
          </div>

          <div className="mt-12 flex flex-wrap items-center justify-between gap-4 border-t border-white/10 pt-6 text-sm">
            <span>{legal?.copyright ?? `© ${new Date().getFullYear()} ${companyName}`}</span>

            {navigation.legal.length > 0 && (
              <nav aria-label="Hüquqi linklər">
                <ul className="flex flex-wrap gap-x-5 gap-y-2">
                  {navigation.legal.map((item) => (
                    <li key={item.id}>
                      <Link to={item.url} className="transition-colors hover:text-white">
                        {item.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            )}
          </div>
        </div>
      </footer>

      {/* Renders nothing unless chat is enabled in the published configuration. */}
      <ChatWidget />
      <CompareBar />
    </div>
  );
}

/**
 * One top-level header link. Items with children open a dropdown on hover and focus,
 * so the sub-menu is reachable by keyboard as well as pointer.
 */
function HeaderLink({ item }: { item: MenuItem }) {
  const hasChildren = item.children.length > 0;

  const linkClasses = ({ isActive }: { isActive: boolean }) =>
    clsx(
      'rounded-[var(--radius-button)] px-3.5 py-2 text-sm font-medium transition-colors',
      item.highlight
        ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:bg-[var(--color-primary-hover)]'
        : isActive
          ? 'text-[var(--color-primary)]'
          : 'text-[var(--color-body)] hover:text-[var(--color-heading)]',
    );

  if (!hasChildren) {
    return item.target === '_blank' ? (
      <a
        href={item.url}
        target="_blank"
        rel="noopener noreferrer"
        className={linkClasses({ isActive: false })}
      >
        {item.title}
      </a>
    ) : (
      <NavLink to={item.url} end={item.url === '/'} className={linkClasses}>
        {item.title}
      </NavLink>
    );
  }

  return (
    <div className="group relative">
      <NavLink to={item.url} end={item.url === '/'} className={linkClasses}>
        {item.title}
      </NavLink>

      <div className="invisible absolute start-0 top-full z-50 min-w-56 pt-2 opacity-0 transition-all group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100">
        <ul className="overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-canvas)] py-1.5 shadow-[var(--shadow-popover)]">
          {item.children.map((child) => (
            <li key={child.id}>
              <Link
                to={child.url}
                className="block px-4 py-2.5 text-sm text-[var(--color-body)] transition-colors hover:bg-[var(--color-muted)] hover:text-[var(--color-heading)]"
              >
                {child.title}
                {child.description && (
                  <span className="mt-0.5 block text-xs text-[var(--color-subtle)]">
                    {child.description}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
