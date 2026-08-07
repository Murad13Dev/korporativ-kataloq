import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, ChevronRight, Folders, Pencil, Plus, Trash2 } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  InlineLoader,
  Input,
  PageHeader,
  Select,
  Textarea,
  Toggle,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData, putData } from '@/lib/api';
import type { CategoryNode } from '@/types/api';

interface CategoryFormState {
  id: number | null;
  parentId: number | null;
  slug: string;
  name: string;
  description: string;
  status: 'ACTIVE' | 'DRAFT' | 'ARCHIVED';
  featured: boolean;
  showInMenu: boolean;
}

const EMPTY_FORM: CategoryFormState = {
  id: null,
  parentId: null,
  slug: '',
  name: '',
  description: '',
  status: 'ACTIVE',
  featured: false,
  showInMenu: true,
};

/**
 * Category tree.
 *
 * The tree and the editor sit side by side rather than on separate routes: categories are
 * usually edited in bursts (rename three, reorder two), and a full page navigation between
 * each one loses the operator's place in the hierarchy.
 */
export function CategoriesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [form, setForm] = useState<CategoryFormState | null>(null);
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());

  const tree = useQuery({
    queryKey: ['admin', 'categories', 'tree'],
    queryFn: () => getData<CategoryNode[]>('/admin/categories/tree'),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] });
    queryClient.invalidateQueries({ queryKey: ['categories'] });
  };

  const save = useMutation({
    mutationFn: (values: CategoryFormState) => {
      const payload = {
        parentId: values.parentId,
        slug: values.slug || undefined,
        status: values.status,
        featured: values.featured,
        showInMenu: values.showInMenu,
        // The default language is mandatory server-side; the editor writes `az`
        // and leaves other languages to be filled in per-category later.
        translations: {
          az: {
            name: values.name,
            description: values.description || null,
          },
        },
      };
      return values.id
        ? putData(`/admin/categories/${values.id}`, payload)
        : postData('/admin/categories', payload);
    },
    onSuccess: () => {
      invalidate();
      setForm(null);
      toast.success('Kateqoriya yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/categories/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('Kateqoriya silindi');
    },
    onError: (error: ApiError) => {
      // The server blocks deletion when children or active products exist; that
      // message is more useful than a generic failure.
      toast.error(error.message);
    },
  });

  const toggleCollapse = (id: number) => {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const flatCategories = flatten(tree.data ?? []);

  return (
    <>
      <PageHeader
        title="Kateqoriyalar"
        description="Kataloqun iyerarxiyası. Alt kateqoriyalar valideynin endirimlərini miras alır."
        actions={
          <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setForm({ ...EMPTY_FORM })}>
            Yeni kateqoriya
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <Card bodyClassName="p-0">
          {tree.isLoading ? (
            <InlineLoader />
          ) : !tree.data || tree.data.length === 0 ? (
            <EmptyState
              icon={<Folders size={36} aria-hidden="true" />}
              title="Kateqoriya yoxdur"
              description="Məhsulları qruplaşdırmaq üçün ilk kateqoriyanı yaradın."
              action={
                <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setForm({ ...EMPTY_FORM })}>
                  Yeni kateqoriya
                </Button>
              }
            />
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {tree.data.map((node) => (
                <CategoryRow
                  key={node.id}
                  node={node}
                  depth={0}
                  collapsed={collapsed}
                  onToggle={toggleCollapse}
                  onEdit={(target) =>
                    setForm({
                      id: target.id,
                      parentId: target.parentId,
                      slug: target.slug,
                      name: target.name,
                      description: '',
                      status: target.status as CategoryFormState['status'],
                      featured: target.featured,
                      showInMenu: target.showInMenu,
                    })
                  }
                  onDelete={(target) => {
                    if (window.confirm(`"${target.name}" kateqoriyası silinsin?`)) {
                      remove.mutate(target.id);
                    }
                  }}
                  onAddChild={(parentId) => setForm({ ...EMPTY_FORM, parentId })}
                />
              ))}
            </ul>
          )}
        </Card>

        {form && (
          <Card
            title={form.id ? 'Kateqoriyanı redaktə et' : 'Yeni kateqoriya'}
            className="lg:sticky lg:top-24 lg:self-start"
            actions={
              <Button variant="ghost" size="sm" onClick={() => setForm(null)}>
                Bağla
              </Button>
            }
          >
            <div className="space-y-4">
              <Field label="Ad" htmlFor="categoryName" required>
                <Input
                  id="categoryName"
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </Field>

              <Field
                label="Slug"
                htmlFor="categorySlug"
                hint="Boş qoyulsa addan avtomatik yaradılır"
              >
                <Input
                  id="categorySlug"
                  value={form.slug}
                  onChange={(event) => setForm({ ...form, slug: event.target.value })}
                  placeholder="avtomatik"
                />
              </Field>

              <Field label="Valideyn kateqoriya" htmlFor="parentId">
                <Select
                  id="parentId"
                  value={form.parentId ?? ''}
                  onChange={(event) =>
                    setForm({ ...form, parentId: event.target.value ? Number(event.target.value) : null })
                  }
                >
                  <option value="">Kök kateqoriya</option>
                  {flatCategories
                    .filter((candidate) => candidate.id !== form.id)
                    .map((candidate) => (
                      <option key={candidate.id} value={candidate.id}>
                        {'— '.repeat(candidate.depth)}
                        {candidate.name}
                      </option>
                    ))}
                </Select>
              </Field>

              <Field label="Təsvir" htmlFor="categoryDescription">
                <Textarea
                  id="categoryDescription"
                  rows={3}
                  value={form.description}
                  onChange={(event) => setForm({ ...form, description: event.target.value })}
                />
              </Field>

              <Field label="Status" htmlFor="categoryStatus">
                <Select
                  id="categoryStatus"
                  value={form.status}
                  onChange={(event) =>
                    setForm({ ...form, status: event.target.value as CategoryFormState['status'] })
                  }
                >
                  <option value="ACTIVE">Aktiv</option>
                  <option value="DRAFT">Qaralama</option>
                  <option value="ARCHIVED">Arxiv</option>
                </Select>
              </Field>

              <Toggle
                label="Seçilmiş"
                hint="Ana səhifədə göstərilir"
                checked={form.featured}
                onChange={(checked) => setForm({ ...form, featured: checked })}
              />

              <Toggle
                label="Menyuda göstər"
                checked={form.showInMenu}
                onChange={(checked) => setForm({ ...form, showInMenu: checked })}
              />

              <Button
                className="w-full"
                loading={save.isPending}
                disabled={!form.name.trim()}
                onClick={() => save.mutate(form)}
              >
                Yadda saxla
              </Button>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}

function CategoryRow({
  node,
  depth,
  collapsed,
  onToggle,
  onEdit,
  onDelete,
  onAddChild,
}: {
  node: CategoryNode;
  depth: number;
  collapsed: Set<number>;
  onToggle: (id: number) => void;
  onEdit: (node: CategoryNode) => void;
  onDelete: (node: CategoryNode) => void;
  onAddChild: (parentId: number) => void;
}) {
  const hasChildren = node.children.length > 0;
  const isCollapsed = collapsed.has(node.id);

  return (
    <>
      <li>
        <div
          className="flex items-center gap-2 px-5 py-3 transition-colors hover:bg-[var(--color-muted)]"
          style={{ paddingInlineStart: `${1.25 + depth * 1.5}rem` }}
        >
          {hasChildren ? (
            <button
              type="button"
              onClick={() => onToggle(node.id)}
              aria-label={isCollapsed ? 'Aç' : 'Bağla'}
              aria-expanded={!isCollapsed}
              className="rounded p-0.5 text-[var(--color-subtle)] hover:text-[var(--color-heading)]"
            >
              {isCollapsed ? (
                <ChevronRight size={15} aria-hidden="true" />
              ) : (
                <ChevronDown size={15} aria-hidden="true" />
              )}
            </button>
          ) : (
            <span className="w-[19px]" />
          )}

          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium text-[var(--color-heading)]">
              {node.name}
            </span>
            <span className="block text-xs text-[var(--color-subtle)]">/{node.slug}</span>
          </span>

          <span className="text-xs tabular text-[var(--color-subtle)]">{node.productCount}</span>

          {node.featured && <Badge tone="info">Seçilmiş</Badge>}
          {node.status !== 'ACTIVE' && (
            <Badge tone={node.status === 'DRAFT' ? 'warning' : 'neutral'}>
              {node.status === 'DRAFT' ? 'Qaralama' : 'Arxiv'}
            </Badge>
          )}

          <div className="flex items-center gap-0.5">
            <Button
              variant="ghost"
              size="sm"
              title="Alt kateqoriya əlavə et"
              onClick={() => onAddChild(node.id)}
              icon={<Plus size={14} aria-hidden="true" />}
            />
            <Button
              variant="ghost"
              size="sm"
              title="Redaktə et"
              onClick={() => onEdit(node)}
              icon={<Pencil size={14} aria-hidden="true" />}
            />
            <Button
              variant="ghost"
              size="sm"
              title="Sil"
              className="text-[var(--color-danger)]"
              onClick={() => onDelete(node)}
              icon={<Trash2 size={14} aria-hidden="true" />}
            />
          </div>
        </div>
      </li>

      {hasChildren &&
        !isCollapsed &&
        node.children.map((child) => (
          <CategoryRow
            key={child.id}
            node={child}
            depth={depth + 1}
            collapsed={collapsed}
            onToggle={onToggle}
            onEdit={onEdit}
            onDelete={onDelete}
            onAddChild={onAddChild}
          />
        ))}
    </>
  );
}

/** Flattens the tree for the parent picker, keeping depth for the indent prefix. */
function flatten(
  nodes: CategoryNode[],
  depth = 0,
): { id: number; name: string; depth: number }[] {
  return nodes.flatMap((node) => [
    { id: node.id, name: node.name, depth },
    ...flatten(node.children, depth + 1),
  ]);
}
