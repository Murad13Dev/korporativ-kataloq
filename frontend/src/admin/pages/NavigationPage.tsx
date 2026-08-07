import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  ChevronDown,
  ChevronUp,
  CornerDownRight,
  Eye,
  EyeOff,
  Menu as MenuIcon,
  Plus,
  Rocket,
  Trash2,
} from 'lucide-react';
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
  Toggle,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getData, postData, putData } from '@/lib/api';

type Location = 'HEADER' | 'FOOTER_PRIMARY' | 'FOOTER_SECONDARY' | 'MOBILE' | 'LEGAL';
type LinkType = 'CUSTOM' | 'PAGE' | 'CATEGORY' | 'PRODUCT' | 'POST' | 'EXTERNAL';

interface MenuRow {
  id: number;
  code: string;
  name: string;
  location: Location;
  locationLabel: string;
  active: boolean;
  itemCount: number;
  hasDraft: boolean;
  publishedAt: string | null;
}

interface MenuItemNode {
  id: number;
  parentId: number | null;
  title: string;
  url: string;
  linkType: LinkType;
  referenceId: number | null;
  icon: string | null;
  description: string | null;
  target: '_self' | '_blank';
  highlight: boolean;
  sortOrder: number;
  active: boolean;
  children: MenuItemNode[];
}

interface MenuVersion {
  id: number;
  menuId: number;
  menuCode: string;
  menuName: string;
  location: Location;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedAt: string | null;
  items: MenuItemNode[];
}

interface ItemForm {
  id: number | null;
  parentId: number | null;
  title: string;
  url: string;
  linkType: LinkType;
  icon: string;
  description: string;
  target: '_self' | '_blank';
  highlight: boolean;
  active: boolean;
}

const EMPTY_ITEM: ItemForm = {
  id: null,
  parentId: null,
  title: '',
  url: '/',
  linkType: 'CUSTOM',
  icon: '',
  description: '',
  target: '_self',
  highlight: false,
  active: true,
};

const LOCATIONS: { value: Location; label: string }[] = [
  { value: 'HEADER', label: 'Əsas menyu (header)' },
  { value: 'FOOTER_PRIMARY', label: 'Footer — birinci sütun' },
  { value: 'FOOTER_SECONDARY', label: 'Footer — ikinci sütun' },
  { value: 'MOBILE', label: 'Mobil menyu' },
  { value: 'LEGAL', label: 'Hüquqi linklər' },
];

/**
 * Navigation editor.
 *
 * Menus are versioned like pages: items are rearranged on a draft and the header only
 * changes when the operator publishes. Reordering is arrow-based rather than
 * drag-and-drop — a two-level tree has few enough items that arrows are faster, work on
 * touch, and are reachable by keyboard, which a drag handle is not.
 */
export function NavigationPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [selectedMenuId, setSelectedMenuId] = useState<number | null>(null);
  const [itemForm, setItemForm] = useState<ItemForm | null>(null);
  const [creatingMenu, setCreatingMenu] = useState(false);

  const menus = useQuery({
    queryKey: ['admin', 'menus'],
    queryFn: () => getData<MenuRow[]>('/admin/menus'),
  });

  // Auto-select the first menu so the screen is never empty on arrival.
  const activeMenuId = selectedMenuId ?? menus.data?.[0]?.id ?? null;

  const version = useQuery({
    queryKey: ['admin', 'menu', activeMenuId, 'editable'],
    queryFn: () => getData<MenuVersion>(`/admin/menus/${activeMenuId}/editable`),
    enabled: activeMenuId !== null,
  });

  const data = version.data;
  const isDraft = data?.status === 'DRAFT';

  const refresh = (fresh: MenuVersion) => {
    queryClient.setQueryData(['admin', 'menu', activeMenuId, 'editable'], fresh);
  };

  const invalidateMenus = () => queryClient.invalidateQueries({ queryKey: ['admin', 'menus'] });

  // --- mutations ----------------------------------------------------------

  const createMenu = useMutation({
    mutationFn: (values: { code: string; name: string; location: Location }) =>
      postData<MenuRow>('/admin/menus', values),
    onSuccess: (menu) => {
      invalidateMenus();
      setSelectedMenuId(menu.id);
      setCreatingMenu(false);
      toast.success('Menyu yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const createDraft = useMutation({
    mutationFn: () => postData<MenuVersion>(`/admin/menus/${activeMenuId}/draft`),
    onSuccess: (fresh) => {
      refresh(fresh);
      invalidateMenus();
      toast.success('Qaralama açıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const publish = useMutation({
    mutationFn: () => postData<MenuVersion>(`/admin/menus/${activeMenuId}/publish`),
    onSuccess: (fresh) => {
      refresh(fresh);
      invalidateMenus();
      toast.success('Menyu dərc edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const discardDraft = useMutation({
    mutationFn: () => deleteRequest(`/admin/menus/${activeMenuId}/draft`),
    onSuccess: () => {
      invalidateMenus();
      version.refetch();
      toast.success('Qaralama ləğv edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const saveItem = useMutation({
    mutationFn: (values: ItemForm) => {
      const payload = {
        parentId: values.parentId,
        title: values.title,
        url: values.url,
        linkType: values.linkType,
        icon: values.icon || null,
        description: values.description || null,
        target: values.target,
        highlight: values.highlight,
        active: values.active,
      };
      return values.id
        ? putData<MenuVersion>(`/admin/menus/versions/${data!.id}/items/${values.id}`, payload)
        : postData<MenuVersion>(`/admin/menus/versions/${data!.id}/items`, payload);
    },
    onSuccess: (fresh) => {
      refresh(fresh);
      setItemForm(null);
      toast.success('Element yadda saxlanıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const removeItem = useMutation({
    mutationFn: (itemId: number) =>
      deleteRequest(`/admin/menus/versions/${data!.id}/items/${itemId}`).then(() =>
        getData<MenuVersion>(`/admin/menus/${activeMenuId}/editable`),
      ),
    onSuccess: (fresh) => {
      refresh(fresh);
      toast.success('Element silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const reorder = useMutation({
    mutationFn: (items: { id: number; parentId: number | null; sortOrder: number }[]) =>
      putData<MenuVersion>(`/admin/menus/versions/${data!.id}/items/reorder`, { items }),
    onSuccess: (fresh) => refresh(fresh),
    onError: (error: ApiError) => toast.error(error.message),
  });

  /** Moves an item within its own sibling group and resends the whole group's order. */
  const move = (item: MenuItemNode, direction: -1 | 1) => {
    if (!data) return;
    const siblings = item.parentId === null
      ? data.items
      : (findNode(data.items, item.parentId)?.children ?? []);

    const ordered = [...siblings].sort((a, b) => a.sortOrder - b.sortOrder);
    const index = ordered.findIndex((candidate) => candidate.id === item.id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= ordered.length) return;

    [ordered[index], ordered[target]] = [ordered[target]!, ordered[index]!];
    reorder.mutate(
      ordered.map((node, position) => ({
        id: node.id,
        parentId: node.parentId,
        sortOrder: position,
      })),
    );
  };

  /** Nests an item under the sibling above it, or lifts it back to the root. */
  const changeDepth = (item: MenuItemNode, nest: boolean) => {
    if (!data) return;

    if (nest) {
      const roots = [...data.items].sort((a, b) => a.sortOrder - b.sortOrder);
      const index = roots.findIndex((candidate) => candidate.id === item.id);
      const newParent = roots[index - 1];
      if (!newParent) return;
      reorder.mutate([
        { id: item.id, parentId: newParent.id, sortOrder: newParent.children.length },
      ]);
    } else {
      reorder.mutate([{ id: item.id, parentId: null, sortOrder: data.items.length }]);
    }
  };

  return (
    <>
      <PageHeader
        title="Naviqasiya"
        description="Header və footer menyuları. Dəyişikliklər dərc olunana qədər saytda görünmür."
        actions={
          <>
            <Button variant="secondary" onClick={() => setCreatingMenu((open) => !open)}>
              Yeni menyu
            </Button>
            {data && !isDraft && (
              <Button loading={createDraft.isPending} onClick={() => createDraft.mutate()}>
                Redaktə et
              </Button>
            )}
            {data && isDraft && (
              <Button
                loading={publish.isPending}
                icon={<Rocket size={15} aria-hidden="true" />}
                onClick={() => {
                  if (window.confirm('Menyu saytda dərhal dəyişəcək. Davam edilsin?')) {
                    publish.mutate();
                  }
                }}
              >
                Dərc et
              </Button>
            )}
          </>
        }
      />

      {creatingMenu && (
        <CreateMenuForm
          pending={createMenu.isPending}
          onCancel={() => setCreatingMenu(false)}
          onSubmit={(values) => createMenu.mutate(values)}
        />
      )}

      <div className="grid gap-6 lg:grid-cols-[240px_1fr_340px]">
        {/* ---------------- menu list ---------------- */}
        <Card bodyClassName="p-0">
          {menus.isLoading ? (
            <InlineLoader />
          ) : !menus.data || menus.data.length === 0 ? (
            <EmptyState
              icon={<MenuIcon size={30} aria-hidden="true" />}
              title="Menyu yoxdur"
              description="İlk menyunu yaradın."
            />
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {menus.data.map((menu) => (
                <li key={menu.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedMenuId(menu.id);
                      setItemForm(null);
                    }}
                    className={clsx(
                      'w-full px-4 py-3 text-start transition-colors',
                      activeMenuId === menu.id
                        ? 'bg-[var(--color-primary)]/6'
                        : 'hover:bg-[var(--color-muted)]',
                    )}
                  >
                    <span className="flex items-center gap-2">
                      <span className="min-w-0 flex-1 truncate text-sm font-medium text-[var(--color-heading)]">
                        {menu.name}
                      </span>
                      {menu.hasDraft && <Badge tone="warning">•</Badge>}
                    </span>
                    <span className="block text-xs text-[var(--color-subtle)]">
                      {menu.locationLabel}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* ---------------- item tree ---------------- */}
        <Card
          title={data?.menuName ?? 'Menyu'}
          description={data ? `v${data.versionNumber}` : undefined}
          bodyClassName="p-0"
          actions={
            isDraft && (
              <Button
                size="sm"
                icon={<Plus size={14} aria-hidden="true" />}
                onClick={() => setItemForm({ ...EMPTY_ITEM })}
              >
                Element əlavə et
              </Button>
            )
          }
        >
          {version.isLoading ? (
            <InlineLoader />
          ) : !data ? (
            <EmptyState title="Menyu seçin" description="Soldakı siyahıdan menyu seçin." />
          ) : (
            <>
              <div
                className={clsx(
                  'flex items-center gap-3 border-b border-[var(--color-border)] px-4 py-2.5 text-xs',
                  isDraft ? 'bg-[var(--color-warning)]/8' : 'bg-[var(--color-success)]/8',
                )}
              >
                <Badge tone={isDraft ? 'warning' : 'success'}>
                  {isDraft ? 'Qaralama' : 'Canlı'}
                </Badge>
                <span className="flex-1 text-[var(--color-body)]">
                  {isDraft
                    ? 'Dəyişikliklər saytda hələ görünmür.'
                    : 'Saytda görünən versiya. Dəyişmək üçün “Redaktə et”.'}
                </span>
                {isDraft && (
                  <button
                    type="button"
                    onClick={() => {
                      if (window.confirm('Qaralama silinsin?')) discardDraft.mutate();
                    }}
                    className="font-medium text-[var(--color-danger)] hover:underline"
                  >
                    Ləğv et
                  </button>
                )}
              </div>

              {data.items.length === 0 ? (
                <div className="px-4 py-12 text-center">
                  <p className="mb-4 text-sm text-[var(--color-subtle)]">Menyuda element yoxdur.</p>
                  {isDraft && (
                    <Button
                      icon={<Plus size={15} aria-hidden="true" />}
                      onClick={() => setItemForm({ ...EMPTY_ITEM })}
                    >
                      İlk elementi əlavə et
                    </Button>
                  )}
                </div>
              ) : (
                <ul className="divide-y divide-[var(--color-border)]">
                  {data.items.map((item, index) => (
                    <ItemRow
                      key={item.id}
                      item={item}
                      depth={0}
                      isFirst={index === 0}
                      isLast={index === data.items.length - 1}
                      editable={isDraft}
                      onEdit={(node) =>
                        setItemForm({
                          id: node.id,
                          parentId: node.parentId,
                          title: node.title,
                          url: node.url,
                          linkType: node.linkType,
                          icon: node.icon ?? '',
                          description: node.description ?? '',
                          target: node.target,
                          highlight: node.highlight,
                          active: node.active,
                        })
                      }
                      onMove={move}
                      onNest={changeDepth}
                      onToggle={(node) =>
                        saveItem.mutate({
                          id: node.id,
                          parentId: node.parentId,
                          title: node.title,
                          url: node.url,
                          linkType: node.linkType,
                          icon: node.icon ?? '',
                          description: node.description ?? '',
                          target: node.target,
                          highlight: node.highlight,
                          active: !node.active,
                        })
                      }
                      onDelete={(node) => {
                        const message = node.children.length > 0
                          ? `"${node.title}" və onun ${node.children.length} alt elementi silinsin?`
                          : `"${node.title}" silinsin?`;
                        if (window.confirm(message)) {
                          removeItem.mutate(node.id);
                        }
                      }}
                    />
                  ))}
                </ul>
              )}
            </>
          )}
        </Card>

        {/* ---------------- item editor ---------------- */}
        <div className="lg:sticky lg:top-24 lg:self-start">
          {itemForm ? (
            <Card
              title={itemForm.id ? 'Elementi redaktə et' : 'Yeni element'}
              actions={
                <Button variant="ghost" size="sm" onClick={() => setItemForm(null)}>
                  Bağla
                </Button>
              }
            >
              <div className="space-y-4">
                <Field label="Ad" htmlFor="itemTitle" required>
                  <Input
                    id="itemTitle"
                    value={itemForm.title}
                    onChange={(event) => setItemForm({ ...itemForm, title: event.target.value })}
                    placeholder="Haqqımızda"
                  />
                </Field>

                <Field label="Link tipi" htmlFor="linkType">
                  <Select
                    id="linkType"
                    value={itemForm.linkType}
                    onChange={(event) =>
                      setItemForm({ ...itemForm, linkType: event.target.value as LinkType })
                    }
                  >
                    <option value="CUSTOM">Sərbəst link</option>
                    <option value="PAGE">Səhifə</option>
                    <option value="CATEGORY">Kateqoriya</option>
                    <option value="EXTERNAL">Xarici link</option>
                  </Select>
                </Field>

                <Field
                  label="Ünvan"
                  htmlFor="itemUrl"
                  required
                  hint="Daxili: /haqqimizda · Xarici: https://…"
                >
                  <Input
                    id="itemUrl"
                    value={itemForm.url}
                    onChange={(event) => setItemForm({ ...itemForm, url: event.target.value })}
                  />
                </Field>

                <Field label="Valideyn element" htmlFor="itemParent" hint="Alt menyu yaratmaq üçün">
                  <Select
                    id="itemParent"
                    value={itemForm.parentId ?? ''}
                    onChange={(event) =>
                      setItemForm({
                        ...itemForm,
                        parentId: event.target.value ? Number(event.target.value) : null,
                      })
                    }
                  >
                    <option value="">Kök element</option>
                    {(data?.items ?? [])
                      .filter((candidate) => candidate.id !== itemForm.id)
                      .map((candidate) => (
                        <option key={candidate.id} value={candidate.id}>
                          {candidate.title}
                        </option>
                      ))}
                  </Select>
                </Field>

                <Field label="Təsvir" htmlFor="itemDescription" hint="Mega menyuda göstərilir">
                  <Input
                    id="itemDescription"
                    value={itemForm.description}
                    onChange={(event) =>
                      setItemForm({ ...itemForm, description: event.target.value })
                    }
                  />
                </Field>

                <Field label="Açılma" htmlFor="itemTarget">
                  <Select
                    id="itemTarget"
                    value={itemForm.target}
                    onChange={(event) =>
                      setItemForm({ ...itemForm, target: event.target.value as '_self' | '_blank' })
                    }
                  >
                    <option value="_self">Eyni pəncərədə</option>
                    <option value="_blank">Yeni pəncərədə</option>
                  </Select>
                </Field>

                <Toggle
                  label="Düymə kimi göstər"
                  hint="Adi link əvəzinə vurğulanmış düymə"
                  checked={itemForm.highlight}
                  onChange={(checked) => setItemForm({ ...itemForm, highlight: checked })}
                />

                <Toggle
                  label="Görünsün"
                  checked={itemForm.active}
                  onChange={(checked) => setItemForm({ ...itemForm, active: checked })}
                />

                <Button
                  className="w-full"
                  loading={saveItem.isPending}
                  disabled={!itemForm.title.trim() || !itemForm.url.trim()}
                  onClick={() => saveItem.mutate(itemForm)}
                >
                  Yadda saxla
                </Button>
              </div>
            </Card>
          ) : (
            <Card title="Menyu haqqında">
              <ul className="space-y-3 text-sm text-[var(--color-body)]">
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Menyu <strong className="text-[var(--color-heading)]">iki səviyyəlidir</strong> —
                    alt-alt menyu mobil cihazda əlçatan olmadığı üçün dəstəklənmir.
                  </span>
                </li>
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Valideyn element silinərsə,{' '}
                    <strong className="text-[var(--color-heading)]">alt elementləri də silinir</strong>.
                  </span>
                </li>
                <li className="flex gap-2.5">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-primary)]" />
                  <span>
                    Gizlədilmiş element silinmir — sadəcə saytda göstərilmir.
                  </span>
                </li>
              </ul>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function ItemRow({
  item,
  depth,
  isFirst,
  isLast,
  editable,
  onEdit,
  onMove,
  onNest,
  onToggle,
  onDelete,
}: {
  item: MenuItemNode;
  depth: number;
  isFirst: boolean;
  isLast: boolean;
  editable: boolean;
  onEdit: (item: MenuItemNode) => void;
  onMove: (item: MenuItemNode, direction: -1 | 1) => void;
  onNest: (item: MenuItemNode, nest: boolean) => void;
  onToggle: (item: MenuItemNode) => void;
  onDelete: (item: MenuItemNode) => void;
}) {
  return (
    <>
      <li>
        <div
          className={clsx(
            'flex items-center gap-2 py-2.5 pe-3 transition-colors hover:bg-[var(--color-muted)]',
            !item.active && 'opacity-55',
          )}
          style={{ paddingInlineStart: `${1 + depth * 1.75}rem` }}
        >
          {depth > 0 && (
            <CornerDownRight
              size={13}
              className="shrink-0 text-[var(--color-border)]"
              aria-hidden="true"
            />
          )}

          <span className="flex w-5 shrink-0 flex-col">
            <button
              type="button"
              disabled={!editable || isFirst}
              onClick={() => onMove(item, -1)}
              aria-label="Yuxarı"
              className="text-[var(--color-subtle)] hover:text-[var(--color-heading)] disabled:opacity-25"
            >
              <ChevronUp size={13} aria-hidden="true" />
            </button>
            <button
              type="button"
              disabled={!editable || isLast}
              onClick={() => onMove(item, 1)}
              aria-label="Aşağı"
              className="text-[var(--color-subtle)] hover:text-[var(--color-heading)] disabled:opacity-25"
            >
              <ChevronDown size={13} aria-hidden="true" />
            </button>
          </span>

          <button
            type="button"
            onClick={() => onEdit(item)}
            className="min-w-0 flex-1 text-start"
          >
            <span className="block truncate text-sm font-medium text-[var(--color-heading)]">
              {item.title}
              {item.highlight && (
                <Badge tone="info" className="ms-2">
                  Düymə
                </Badge>
              )}
            </span>
            <span className="block truncate font-mono text-xs text-[var(--color-subtle)]">
              {item.url}
            </span>
          </button>

          {editable && (
            <div className="flex shrink-0 items-center gap-0.5">
              {depth === 0 && !isFirst && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="Alt element et"
                  onClick={() => onNest(item, true)}
                  icon={<CornerDownRight size={13} aria-hidden="true" />}
                />
              )}
              {depth > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="Kök elementə çıxar"
                  onClick={() => onNest(item, false)}
                  icon={<ChevronUp size={13} aria-hidden="true" />}
                />
              )}
              <Button
                variant="ghost"
                size="sm"
                title={item.active ? 'Gizlət' : 'Göstər'}
                onClick={() => onToggle(item)}
                icon={
                  item.active ? (
                    <Eye size={13} aria-hidden="true" />
                  ) : (
                    <EyeOff size={13} aria-hidden="true" />
                  )
                }
              />
              <Button
                variant="ghost"
                size="sm"
                title="Sil"
                className="text-[var(--color-danger)]"
                onClick={() => onDelete(item)}
                icon={<Trash2 size={13} aria-hidden="true" />}
              />
            </div>
          )}
        </div>
      </li>

      {item.children.map((child, index) => (
        <ItemRow
          key={child.id}
          item={child}
          depth={depth + 1}
          isFirst={index === 0}
          isLast={index === item.children.length - 1}
          editable={editable}
          onEdit={onEdit}
          onMove={onMove}
          onNest={onNest}
          onToggle={onToggle}
          onDelete={onDelete}
        />
      ))}
    </>
  );
}

function CreateMenuForm({
  pending,
  onCancel,
  onSubmit,
}: {
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: { code: string; name: string; location: Location }) => void;
}) {
  const [name, setName] = useState('');
  const [location, setLocation] = useState<Location>('HEADER');

  return (
    <Card
      title="Yeni menyu"
      className="mb-6"
      actions={
        <Button variant="ghost" size="sm" onClick={onCancel}>
          Bağla
        </Button>
      }
    >
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Ad" htmlFor="menuName" required className="min-w-[200px] flex-1">
          <Input
            id="menuName"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Əsas menyu"
          />
        </Field>

        <Field label="Yer" htmlFor="menuLocation" className="min-w-[220px]">
          <Select
            id="menuLocation"
            value={location}
            onChange={(event) => setLocation(event.target.value as Location)}
          >
            {LOCATIONS.map((entry) => (
              <option key={entry.value} value={entry.value}>
                {entry.label}
              </option>
            ))}
          </Select>
        </Field>

        <Button
          loading={pending}
          disabled={!name.trim()}
          onClick={() =>
            onSubmit({
              code: name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 64),
              name: name.trim(),
              location,
            })
          }
        >
          Yarat
        </Button>
      </div>
    </Card>
  );
}

function findNode(nodes: MenuItemNode[], id: number): MenuItemNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNode(node.children, id);
    if (found) return found;
  }
  return null;
}
