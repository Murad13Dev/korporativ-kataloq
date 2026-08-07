import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Copy, ImageOff, Package, Plus, Star } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { getData, getPage, patchData, postData } from '@/lib/api';
import { formatNumber, formatPrice } from '@/lib/format';
import type { CategoryCard, PriceInfo, StockStatus } from '@/types/api';

interface ProductRow {
  id: number;
  sku: string;
  slug: string;
  name: string;
  imageUrl: string | null;
  categoryId: number | null;
  categoryName: string | null;
  price: number | null;
  effectivePrice: PriceInfo;
  currency: string;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  featured: boolean;
  purchasable: boolean;
  trackStock: boolean;
  stockQuantity: number;
  stockStatus: StockStatus;
  sortOrder: number;
  viewCount: number;
  updatedAt: string;
}

const STATUS_TONE: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  DRAFT: 'warning',
  ARCHIVED: 'neutral',
};

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Aktiv',
  DRAFT: 'Qaralama',
  ARCHIVED: 'Arxiv',
};

const STOCK_TONE: Record<StockStatus, BadgeTone> = {
  IN_STOCK: 'success',
  LOW_STOCK: 'warning',
  OUT_OF_STOCK: 'danger',
  BACKORDER: 'info',
};

export function ProductsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  // The dashboard's low-stock alert deep-links here with ?lowStock=true; the search box
  // and other chips only need to seed from the URL once, not stay in sync afterwards.
  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(() => searchParams.get('status') ?? '');
  const [categoryId, setCategoryId] = useState('');
  const [discounted, setDiscounted] = useState(false);
  const [lowStock, setLowStock] = useState(() => searchParams.get('lowStock') === 'true');
  const [page, setPage] = useState(0);

  const products = useQuery({
    queryKey: ['admin', 'products', { search, status, categoryId, discounted, lowStock, page }],
    queryFn: () =>
      getPage<ProductRow>('/admin/products', {
        search: search || undefined,
        status: status || undefined,
        categoryId: categoryId || undefined,
        discounted: discounted || undefined,
        lowStock: lowStock || undefined,
        page,
        size: 20,
      }),
    placeholderData: (previous) => previous,
  });

  const categories = useQuery({
    queryKey: ['admin', 'categories', 'flat'],
    queryFn: () => getData<CategoryCard[]>('/admin/categories'),
    staleTime: 5 * 60 * 1000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });

  const toggleFeatured = useMutation({
    mutationFn: ({ id, featured }: { id: number; featured: boolean }) =>
      patchData(`/admin/products/${id}/featured`, { featured }),
    onSuccess: invalidate,
    onError: (error: Error) => toast.error(error.message),
  });

  const duplicate = useMutation({
    mutationFn: (id: number) => postData<{ id: number }>(`/admin/products/${id}/duplicate`),
    onSuccess: () => {
      invalidate();
      toast.success('Məhsul kopyalandı (qaralama olaraq)');
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const rows = products.data;
  const hasFilters = Boolean(search || status || categoryId || discounted || lowStock);

  return (
    <>
      <PageHeader
        title="Məhsullar"
        description="Kataloqdakı bütün məhsullar"
        actions={
          <Link to="/admin/products/new">
            <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni məhsul</Button>
          </Link>
        }
      />

      <Card bodyClassName="p-0">
        <div className="flex flex-wrap items-center gap-3 border-b border-[var(--color-border)] p-4">
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Ad və ya SKU"
            className="max-w-xs"
          />

          <Select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
            className="max-w-[160px]"
          >
            <option value="">Bütün statuslar</option>
            <option value="ACTIVE">Aktiv</option>
            <option value="DRAFT">Qaralama</option>
            <option value="ARCHIVED">Arxiv</option>
          </Select>

          <Select
            value={categoryId}
            onChange={(event) => {
              setCategoryId(event.target.value);
              setPage(0);
            }}
            className="max-w-[200px]"
          >
            <option value="">Bütün kateqoriyalar</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>

          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={discounted}
              onChange={(event) => {
                setDiscounted(event.target.checked);
                setPage(0);
              }}
              className="h-4 w-4 rounded accent-[var(--color-sale)]"
            />
            Yalnız endirimdə
          </label>

          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={lowStock}
              onChange={(event) => {
                setLowStock(event.target.checked);
                setPage(0);
              }}
              className="h-4 w-4 rounded accent-[var(--color-warning)]"
            />
            Anbar qalığı az
          </label>

          {hasFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearch('');
                setStatus('');
                setCategoryId('');
                setDiscounted(false);
                setLowStock(false);
                setPage(0);
              }}
            >
              Təmizlə
            </Button>
          )}
        </div>

        {products.isLoading ? (
          <TableSkeleton rows={8} columns={6} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState
            icon={<Package size={36} aria-hidden="true" />}
            title={hasFilters ? 'Nəticə tapılmadı' : 'Hələ məhsul yoxdur'}
            description={
              hasFilters
                ? 'Filtrləri dəyişməyi sınayın.'
                : 'İlk məhsulunuzu əlavə edərək kataloqu qurmağa başlayın.'
            }
            action={
              !hasFilters && (
                <Link to="/admin/products/new">
                  <Button icon={<Plus size={15} aria-hidden="true" />}>Yeni məhsul</Button>
                </Link>
              )
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                  <th className="px-5 py-3 text-start font-semibold">Məhsul</th>
                  <th className="px-5 py-3 text-start font-semibold">Kateqoriya</th>
                  <th className="px-5 py-3 text-end font-semibold">Qiymət</th>
                  <th className="px-5 py-3 text-start font-semibold">Anbar</th>
                  <th className="px-5 py-3 text-start font-semibold">Status</th>
                  <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {rows.content.map((product) => (
                  <tr key={product.id} className="transition-colors hover:bg-[var(--color-muted)]">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-3">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
                          {product.imageUrl ? (
                            <img
                              src={product.imageUrl}
                              alt=""
                              loading="lazy"
                              className="h-full w-full object-cover"
                            />
                          ) : (
                            <ImageOff size={16} className="text-[var(--color-subtle)]" aria-hidden="true" />
                          )}
                        </div>
                        <div className="min-w-0">
                          <Link
                            to={`/admin/products/${product.id}`}
                            className="block truncate font-medium text-[var(--color-heading)] hover:text-[var(--color-primary)]"
                          >
                            {product.name}
                          </Link>
                          <p className="text-xs text-[var(--color-subtle)]">{product.sku}</p>
                        </div>
                      </div>
                    </td>

                    <td className="px-5 py-3 text-[var(--color-body)]">
                      {product.categoryName ?? (
                        <span className="text-[var(--color-subtle)]">—</span>
                      )}
                    </td>

                    <td className="px-5 py-3 text-end">
                      {product.price === null ? (
                        <span className="text-xs text-[var(--color-subtle)]">Sorğu ilə</span>
                      ) : product.effectivePrice.discounted ? (
                        <>
                          <span className="block font-semibold tabular text-[var(--color-sale)]">
                            {formatPrice(product.effectivePrice.finalPrice)}
                          </span>
                          <span className="block text-xs tabular text-[var(--color-subtle)] line-through">
                            {formatPrice(product.price)}
                          </span>
                        </>
                      ) : (
                        <span className="font-semibold tabular text-[var(--color-heading)]">
                          {formatPrice(product.price)}
                        </span>
                      )}
                    </td>

                    <td className="px-5 py-3">
                      {product.trackStock ? (
                        <Badge tone={STOCK_TONE[product.stockStatus]}>
                          {product.stockQuantity} ədəd
                        </Badge>
                      ) : (
                        <span className="text-xs text-[var(--color-subtle)]">İzlənmir</span>
                      )}
                    </td>

                    <td className="px-5 py-3">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Badge tone={STATUS_TONE[product.status] ?? 'neutral'}>
                          {STATUS_LABEL[product.status]}
                        </Badge>
                        {product.effectivePrice.discounted && (
                          <Badge tone="sale">{product.effectivePrice.badgeLabel}</Badge>
                        )}
                      </div>
                    </td>

                    <td className="px-5 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          title={product.featured ? 'Seçilmişdən çıxar' : 'Seçilmişə əlavə et'}
                          onClick={() =>
                            toggleFeatured.mutate({ id: product.id, featured: !product.featured })
                          }
                          icon={
                            <Star
                              size={15}
                              className={clsx(
                                product.featured && 'fill-[var(--color-warning)] text-[var(--color-warning)]',
                              )}
                              aria-hidden="true"
                            />
                          }
                        />
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Kopyala"
                          onClick={() => duplicate.mutate(product.id)}
                          icon={<Copy size={15} aria-hidden="true" />}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {rows && rows.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
            <span className="text-xs text-[var(--color-subtle)]">
              {formatNumber(rows.totalElements)} məhsul · səhifə {rows.page + 1}/{rows.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={rows.first}
                onClick={() => setPage((current) => current - 1)}
              >
                Əvvəlki
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={rows.last}
                onClick={() => setPage((current) => current + 1)}
              >
                Növbəti
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
