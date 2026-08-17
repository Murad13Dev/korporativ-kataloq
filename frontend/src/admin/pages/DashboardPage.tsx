import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  AlertTriangle,
  ArrowRight,
  Globe,
  Info,
  Package,
  Radio,
  ShoppingBag,
  Tag,
  TrendingDown,
  TrendingUp,
  UsersRound,
} from 'lucide-react';
import { Badge, Card, InlineLoader, PageHeader, type BadgeTone } from '../components/ui';
import { getData } from '@/lib/api';
import { formatDateTime, formatNumber, formatPrice } from '@/lib/format';

// Mirrors DashboardDtos.Summary on the server.
interface Summary {
  products: { total: number; active: number; draft: number; archived: number; lowStock: number };
  categories: { total: number };
  orders: {
    total: number; pending: number; awaitingPayment: number; processing: number;
    completed: number; cancelled: number; todayCount: number; monthCount: number;
    revenueTotal: number; revenueThisMonth: number; currency: string;
  };
  customers: { total: number; newThisMonth: number };
  content: { posts: number; publishedPosts: number; contactRequests: number; newContactRequests: number };
  discounts: { running: number; scheduled: number; discountedProducts: number };
  media: { fileCount: number; totalBytes: number };
  recentOrders: {
    id: number; orderNumber: string; customerName: string; status: string; statusLabel: string;
    paymentStatus: string; total: number; currency: string; createdAt: string;
  }[];
  recentContactRequests: {
    id: number; name: string; email: string; subject: string; status: string; createdAt: string;
  }[];
  lowStockProducts: { id: number; name: string; sku: string; stockQuantity: number; threshold: number }[];
  alerts: { code: string; severity: 'INFO' | 'WARNING' | 'DANGER'; message: string; actionUrl: string; count: number }[];
}

const ORDER_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral',
  AWAITING_PAYMENT: 'warning',
  CONFIRMED: 'info',
  PROCESSING: 'info',
  SHIPPED: 'info',
  DELIVERED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'danger',
  REFUNDED: 'danger',
  EXPIRED: 'neutral',
};

interface LiveVisitors {
  totalViewers: number;
  topProducts: { id: number; name: string; slug: string; viewers: number }[];
}

interface StockForecastItem {
  id: number;
  name: string;
  sku: string;
  stockQuantity: number;
  dailyVelocity: number;
  estimatedDaysRemaining: number;
}

interface ContentCoverage {
  total: number;
  translated: number;
}

interface LanguageCoverage {
  code: string;
  name: string;
  products: ContentCoverage;
  categories: ContentCoverage;
  posts: ContentCoverage;
  pages: ContentCoverage;
}

export function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => getData<Summary>('/admin/dashboard/summary'),
    staleTime: 60 * 1000,
  });

  // A short poll interval, not a WebSocket subscription: this tile is a nice-to-have
  // glance, not something worth holding an extra live connection open for.
  const live = useQuery({
    queryKey: ['admin', 'dashboard', 'live-visitors'],
    queryFn: () => getData<LiveVisitors>('/admin/dashboard/live-visitors'),
    staleTime: 8 * 1000,
    refetchInterval: 10 * 1000,
  });

  const stockForecast = useQuery({
    queryKey: ['admin', 'dashboard', 'stock-forecast'],
    queryFn: () => getData<StockForecastItem[]>('/admin/dashboard/stock-forecast'),
    staleTime: 5 * 60 * 1000,
  });

  const translationCoverage = useQuery({
    queryKey: ['admin', 'dashboard', 'translation-coverage'],
    queryFn: () => getData<LanguageCoverage[]>('/admin/dashboard/translation-coverage'),
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading || !data) {
    return <InlineLoader label="Göstəricilər yüklənir…" />;
  }

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Sistemin cari vəziyyəti"
      />

      {/* Alerts come first: these are the things that need a decision today. */}
      {data.alerts.length > 0 && (
        <div className="mb-6 space-y-2">
          {data.alerts.map((alert) => (
            <Link
              key={alert.code}
              to={alert.actionUrl}
              className={clsx(
                'flex items-center gap-3 rounded-[var(--radius-card)] border px-4 py-3 text-sm transition-colors',
                alert.severity === 'DANGER'
                  ? 'border-[var(--color-danger)]/30 bg-[var(--color-danger)]/8 text-[var(--color-danger)] hover:bg-[var(--color-danger)]/12'
                  : alert.severity === 'WARNING'
                    ? 'border-[var(--color-warning)]/30 bg-[var(--color-warning)]/8 text-[var(--color-warning)] hover:bg-[var(--color-warning)]/12'
                    : 'border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-body)] hover:bg-[var(--color-muted)]',
              )}
            >
              {alert.severity === 'INFO' ? (
                <Info size={17} className="shrink-0" aria-hidden="true" />
              ) : (
                <AlertTriangle size={17} className="shrink-0" aria-hidden="true" />
              )}
              <span className="flex-1 font-medium">{alert.message}</span>
              <ArrowRight size={15} aria-hidden="true" />
            </Link>
          ))}
        </div>
      )}

      {/* ---------------- stat tiles ---------------- */}
      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile
          icon={Package}
          label="Məhsullar"
          value={formatNumber(data.products.total)}
          detail={`${formatNumber(data.products.active)} aktiv · ${formatNumber(data.products.draft)} qaralama`}
          to="/admin/products"
        />
        <StatTile
          icon={ShoppingBag}
          label="Sifarişlər"
          value={formatNumber(data.orders.total)}
          detail={`Bu ay ${formatNumber(data.orders.monthCount)} · bu gün ${formatNumber(data.orders.todayCount)}`}
          to="/admin/orders"
        />
        <StatTile
          icon={TrendingUp}
          label="Bu ayın gəliri"
          value={formatPrice(data.orders.revenueThisMonth)}
          detail={`Ümumi: ${formatPrice(data.orders.revenueTotal)}`}
          tone="success"
        />
        <StatTile
          icon={Tag}
          label="Aktiv kampaniyalar"
          value={formatNumber(data.discounts.running)}
          detail={
            data.discounts.scheduled > 0
              ? `${formatNumber(data.discounts.discountedProducts)} məhsul · ${data.discounts.scheduled} planlaşdırılıb`
              : `${formatNumber(data.discounts.discountedProducts)} məhsul endirimdə`
          }
          to="/admin/discounts"
          tone="sale"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* ---------------- recent orders ---------------- */}
        <Card
          title="Son sifarişlər"
          className="lg:col-span-2"
          bodyClassName="p-0"
          actions={
            <Link
              to="/admin/orders"
              className="text-sm font-medium text-[var(--color-primary)] hover:underline"
            >
              Hamısı
            </Link>
          }
        >
          {data.recentOrders.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-[var(--color-subtle)]">
              Hələ sifariş yoxdur
            </p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {data.recentOrders.map((order) => (
                <li key={order.id}>
                  <Link
                    to={`/admin/orders/${order.id}`}
                    className="flex items-center gap-4 px-5 py-3.5 transition-colors hover:bg-[var(--color-muted)]"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-[var(--color-heading)]">
                        {order.orderNumber}
                      </p>
                      <p className="truncate text-xs text-[var(--color-subtle)]">
                        {order.customerName} · {formatDateTime(order.createdAt)}
                      </p>
                    </div>
                    <Badge tone={ORDER_STATUS_TONE[order.status] ?? 'neutral'}>
                      {order.statusLabel}
                    </Badge>
                    <span className="w-24 text-end text-sm font-semibold tabular text-[var(--color-heading)]">
                      {formatPrice(order.total)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* ---------------- side column ---------------- */}
        <div className="space-y-6">
          {live.data && live.data.totalViewers > 0 && (
            <Card
              title="Canlı ziyarətçilər"
              actions={
                <span className="pulse-dot inline-block h-2 w-2 rounded-full bg-[var(--color-success)] text-[var(--color-success)]" />
              }
            >
              <div className="mb-3 flex items-baseline gap-2">
                <Radio size={18} className="text-[var(--color-success)]" aria-hidden="true" />
                <span className="font-display text-2xl font-bold tabular text-[var(--color-heading)]">
                  {live.data.totalViewers}
                </span>
                <span className="text-sm text-[var(--color-subtle)]">məhsul səhifəsində</span>
              </div>
              <ul className="space-y-2">
                {live.data.topProducts.map((product) => (
                  <li key={product.id}>
                    <Link
                      to={`/admin/products?search=${encodeURIComponent(product.name)}`}
                      className="flex items-center justify-between gap-3 rounded-[var(--radius-button)] px-2 py-1.5 text-sm transition-colors hover:bg-[var(--color-muted)]"
                    >
                      <span className="truncate text-[var(--color-body)]">{product.name}</span>
                      <span className="shrink-0 font-semibold tabular text-[var(--color-heading)]">
                        {product.viewers}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card title="Anbar xəbərdarlığı" bodyClassName="p-0">
            {data.lowStockProducts.length === 0 ? (
              <p className="px-5 py-10 text-center text-sm text-[var(--color-subtle)]">
                Bütün məhsulların qalığı kifayətdir
              </p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {data.lowStockProducts.map((product) => (
                  <li key={product.id}>
                    <Link
                      to={`/admin/products/${product.id}`}
                      className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-[var(--color-muted)]"
                    >
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-[var(--color-heading)]">
                          {product.name}
                        </p>
                        <p className="text-xs text-[var(--color-subtle)]">{product.sku}</p>
                      </div>
                      <Badge tone={product.stockQuantity === 0 ? 'danger' : 'warning'}>
                        {product.stockQuantity} ədəd
                      </Badge>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {stockForecast.data && stockForecast.data.length > 0 && (
            <Card
              title="Anbar radar"
              actions={<TrendingDown size={15} className="text-[var(--color-warning)]" aria-hidden="true" />}
              bodyClassName="p-0"
            >
              <ul className="divide-y divide-[var(--color-border)]">
                {stockForecast.data.map((item) => (
                  <li key={item.id}>
                    <Link
                      to={`/admin/products/${item.id}`}
                      className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-[var(--color-muted)]"
                    >
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-[var(--color-heading)]">{item.name}</p>
                        <p className="text-xs text-[var(--color-subtle)]">
                          {item.stockQuantity} ədəd qalıb · günə ~{item.dailyVelocity} satılır
                        </p>
                      </div>
                      <Badge tone={item.estimatedDaysRemaining <= 7 ? 'danger' : 'warning'}>
                        ~{item.estimatedDaysRemaining} gün
                      </Badge>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card title="Müştərilər">
            <dl className="space-y-3 text-sm">
              <div className="flex items-center justify-between">
                <dt className="flex items-center gap-2 text-[var(--color-body)]">
                  <UsersRound size={15} aria-hidden="true" />
                  Ümumi
                </dt>
                <dd className="font-semibold tabular text-[var(--color-heading)]">
                  {formatNumber(data.customers.total)}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-[var(--color-body)]">Bu ay yeni</dt>
                <dd className="font-semibold tabular text-[var(--color-success)]">
                  +{formatNumber(data.customers.newThisMonth)}
                </dd>
              </div>
              <div className="flex items-center justify-between border-t border-[var(--color-border)] pt-3">
                <dt className="text-[var(--color-body)]">Cavablanmamış sorğu</dt>
                <dd className="font-semibold tabular text-[var(--color-heading)]">
                  {formatNumber(data.content.newContactRequests)}
                </dd>
              </div>
            </dl>
          </Card>

          {translationCoverage.data && translationCoverage.data.length > 0 && (
            <Card
              title="Tərcümə tamlığı"
              actions={<Globe size={15} className="text-[var(--color-primary)]" aria-hidden="true" />}
            >
              <div className="space-y-4">
                {translationCoverage.data.map((lang) => (
                  <div key={lang.code}>
                    <p className="mb-2 text-sm font-semibold text-[var(--color-heading)]">
                      {lang.name} <span className="text-xs font-normal text-[var(--color-subtle)]">({lang.code})</span>
                    </p>
                    <div className="space-y-1.5 text-xs">
                      <CoverageRow label="Məhsullar" coverage={lang.products} />
                      <CoverageRow label="Kateqoriyalar" coverage={lang.categories} />
                      <CoverageRow label="Bloq" coverage={lang.posts} />
                      <CoverageRow label="Səhifələr" coverage={lang.pages} />
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function CoverageRow({ label, coverage }: { label: string; coverage: ContentCoverage }) {
  if (coverage.total === 0) {
    return null;
  }
  const percent = Math.round((coverage.translated / coverage.total) * 100);
  return (
    <div className="flex items-center gap-2">
      <span className="w-24 shrink-0 text-[var(--color-subtle)]">{label}</span>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--color-muted)]">
        <div
          className={clsx(
            'h-full rounded-full',
            percent === 100 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-primary)]',
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
      <span className="w-16 shrink-0 text-end tabular text-[var(--color-subtle)]">
        {coverage.translated}/{coverage.total}
      </span>
    </div>
  );
}

function StatTile({
  icon: Icon,
  label,
  value,
  detail,
  to,
  tone = 'info',
}: {
  icon: typeof Package;
  label: string;
  value: string;
  detail?: string;
  to?: string;
  tone?: 'info' | 'success' | 'sale';
}) {
  const toneClass = {
    info: 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',
    success: 'bg-[var(--color-success)]/10 text-[var(--color-success)]',
    sale: 'bg-[var(--color-sale)]/10 text-[var(--color-sale)]',
  }[tone];

  const content = (
    <div className="flex items-start gap-4 rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 transition-shadow hover:shadow-[var(--shadow-card)]">
      <span className={clsx('flex h-10 w-10 shrink-0 items-center justify-center rounded-[var(--radius-button)]', toneClass)}>
        <Icon size={19} aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase tracking-wide text-[var(--color-subtle)]">
          {label}
        </p>
        <p className="mt-0.5 font-display text-2xl font-bold tabular text-[var(--color-heading)]">
          {value}
        </p>
        {detail && <p className="mt-0.5 truncate text-xs text-[var(--color-subtle)]">{detail}</p>}
      </div>
    </div>
  );

  return to ? <Link to={to}>{content}</Link> : content;
}
