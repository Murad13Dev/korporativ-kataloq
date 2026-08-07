import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Boxes, Plus, Search, Trash2, X } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  PageHeader,
  TableSkeleton,
  Toggle,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getPage, postData, putData } from '@/lib/api';

interface ProductOption {
  id: number;
  name: string;
  sku: string;
}

interface BundleItem {
  productId: number;
  productName: string;
  productSku: string;
}

interface BundleRow {
  id: number;
  name: string;
  primaryProductId: number;
  primaryProductName: string;
  items: BundleItem[];
  active: boolean;
  sortOrder: number;
}

interface BundleFormValues {
  name: string;
  primaryProductId: number | null;
  primaryProductName: string;
  items: ProductOption[];
  active: boolean;
}

const EMPTY_FORM: BundleFormValues = { name: '', primaryProductId: null, primaryProductName: '', items: [], active: true };

/**
 * "Frequently bought together" bundles.
 *
 * A bundle has no price of its own — see the backend's own comment on why — so this
 * screen only ever asks for a name, an anchor product and the cross-sell items. The
 * combined price a shopper sees is always computed live from the items' own prices.
 */
export function BundlesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [editingId, setEditingId] = useState<number | 'new' | null>(null);
  const [form, setForm] = useState<BundleFormValues>(EMPTY_FORM);

  const bundles = useQuery({
    queryKey: ['admin', 'bundles'],
    queryFn: () => getPage<BundleRow>('/admin/bundles', { size: 50 }),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'bundles'] });

  const save = useMutation({
    mutationFn: () => {
      const payload = {
        name: form.name,
        primaryProductId: form.primaryProductId,
        itemProductIds: form.items.map((item) => item.id),
        active: form.active,
      };
      return editingId === 'new'
        ? postData('/admin/bundles', payload)
        : putData(`/admin/bundles/${editingId}`, payload);
    },
    onSuccess: () => {
      invalidate();
      setEditingId(null);
      toast.success('Bundle yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/bundles/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Bundle silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const startEdit = (bundle: BundleRow | 'new') => {
    if (bundle === 'new') {
      setForm(EMPTY_FORM);
      setEditingId('new');
    } else {
      setForm({
        name: bundle.name,
        primaryProductId: bundle.primaryProductId,
        primaryProductName: bundle.primaryProductName,
        items: bundle.items.map((item) => ({ id: item.productId, name: item.productName, sku: item.productSku })),
        active: bundle.active,
      });
      setEditingId(bundle.id);
    }
  };

  const rows = bundles.data;
  const valid = form.name.trim().length > 0 && form.primaryProductId !== null && form.items.length > 0;

  return (
    <>
      <PageHeader
        title="Bundle-lar"
        description="Məhsul səhifəsində göstərilən 'birlikdə tez-tez alınır' siyahıları"
        actions={
          <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => startEdit('new')}>
            Bundle əlavə et
          </Button>
        }
      />

      {editingId !== null && (
        <Card
          title={editingId === 'new' ? 'Yeni bundle' : 'Bundle-ı redaktə et'}
          className="mb-6 border-[var(--color-primary)]/40"
          actions={
            <Button variant="ghost" size="sm" onClick={() => setEditingId(null)}>
              Bağla
            </Button>
          }
        >
          <div className="space-y-4">
            <Field label="Ad" htmlFor="bundleName" required>
              <Input
                id="bundleName"
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Məs: Noutbuk aksesuarları"
              />
            </Field>

            <Field label="Əsas məhsul" hint="Bundle bu məhsulun səhifəsində göstərilir">
              <ProductPicker
                selected={
                  form.primaryProductId
                    ? [{ id: form.primaryProductId, name: form.primaryProductName, sku: '' }]
                    : []
                }
                onAdd={(product) =>
                  setForm((current) => ({ ...current, primaryProductId: product.id, primaryProductName: product.name }))
                }
                onRemove={() => setForm((current) => ({ ...current, primaryProductId: null, primaryProductName: '' }))}
                excludeIds={form.items.map((item) => item.id)}
                single
              />
            </Field>

            <Field label="Birlikdə göstəriləcək məhsullar">
              <ProductPicker
                selected={form.items}
                onAdd={(product) => setForm((current) => ({ ...current, items: [...current.items, product] }))}
                onRemove={(id) =>
                  setForm((current) => ({ ...current, items: current.items.filter((item) => item.id !== id) }))
                }
                excludeIds={form.primaryProductId ? [form.primaryProductId] : []}
              />
            </Field>

            <Toggle
              label="Aktiv"
              checked={form.active}
              onChange={(active) => setForm((current) => ({ ...current, active }))}
            />

            <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
              <Button variant="secondary" onClick={() => setEditingId(null)}>
                Ləğv et
              </Button>
              <Button loading={save.isPending} disabled={!valid} onClick={() => save.mutate()}>
                Yadda saxla
              </Button>
            </div>
          </div>
        </Card>
      )}

      <Card bodyClassName="p-0">
        {bundles.isLoading ? (
          <TableSkeleton rows={5} columns={4} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState
            icon={<Boxes size={32} aria-hidden="true" />}
            title="Bundle yoxdur"
            description="Bir məhsulla birlikdə tez-tez alınan digər məhsulları qruplaşdırın."
          />
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {rows.content.map((bundle) => (
              <li key={bundle.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-[var(--color-heading)]">{bundle.name}</span>
                    <Badge tone={bundle.active ? 'success' : 'neutral'}>
                      {bundle.active ? 'Aktiv' : 'Deaktiv'}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-[var(--color-subtle)]">
                    {bundle.primaryProductName} · {bundle.items.length} əlavə məhsul
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button variant="ghost" size="sm" onClick={() => startEdit(bundle)}>
                    Redaktə
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-[var(--color-danger)]"
                    icon={<Trash2 size={14} aria-hidden="true" />}
                    onClick={() => {
                      if (window.confirm('Bu bundle tamamilə silinsin?')) {
                        remove.mutate(bundle.id);
                      }
                    }}
                  />
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}

/** Search-as-you-type product picker. `single` replaces the selection; otherwise it adds. */
function ProductPicker({
  selected,
  onAdd,
  onRemove,
  excludeIds = [],
  single = false,
}: {
  selected: ProductOption[];
  onAdd: (product: ProductOption) => void;
  onRemove: (id: number) => void;
  excludeIds?: number[];
  single?: boolean;
}) {
  const [query, setQuery] = useState('');

  const results = useQuery({
    queryKey: ['admin', 'products', 'picker', query],
    queryFn: () => getPage<{ id: number; name: string; sku: string }>('/admin/products', { search: query, size: 8 }),
    enabled: query.trim().length >= 2,
  });

  const selectedIds = new Set(selected.map((item) => item.id));
  const options = (results.data?.content ?? []).filter(
    (option) => !selectedIds.has(option.id) && !excludeIds.includes(option.id),
  );

  return (
    <div>
      {selected.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {selected.map((item) => (
            <span
              key={item.id}
              className="flex items-center gap-1.5 rounded-full bg-[var(--color-muted)] py-1 ps-3 pe-1.5 text-xs font-medium text-[var(--color-heading)]"
            >
              {item.name}
              <button
                type="button"
                onClick={() => onRemove(item.id)}
                className="rounded-full p-0.5 hover:bg-[var(--color-border)]"
                aria-label={`${item.name} çıxar`}
              >
                <X size={11} aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}

      {(!single || selected.length === 0) && (
        <div className="relative">
          <Search
            size={15}
            className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-[var(--color-subtle)]"
            aria-hidden="true"
          />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Məhsul axtar (ən azı 2 hərf)…"
            className="ps-9"
          />
          {query.trim().length >= 2 && options.length > 0 && (
            <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-[var(--radius-button)] border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-popover)]">
              {options.map((option) => (
                <li key={option.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onAdd({ id: option.id, name: option.name, sku: option.sku });
                      setQuery('');
                    }}
                    className={clsx(
                      'flex w-full items-center justify-between gap-2 px-3 py-2 text-start text-sm hover:bg-[var(--color-muted)]',
                    )}
                  >
                    <span className="truncate text-[var(--color-heading)]">{option.name}</span>
                    <span className="shrink-0 text-xs text-[var(--color-subtle)]">{option.sku}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
