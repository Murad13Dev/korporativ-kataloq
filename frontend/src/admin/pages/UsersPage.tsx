import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  Field,
  Input,
  PageHeader,
  Select,
  TableSkeleton,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ROLE_LABELS, useAdminAuth, type AdminRole } from '../auth/adminAuth';
import { ApiError, deleteRequest, getPage, patchData, postData, putData } from '@/lib/api';
import { formatDateTime } from '@/lib/format';

interface AdminUserRow {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: AdminRole;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  lastLoginAt: string | null;
  createdAt: string;
}

const ROLE_TONE: Record<AdminRole, BadgeTone> = {
  SUPER_ADMIN: 'danger',
  ADMIN: 'info',
  EDITOR: 'success',
  SUPPORT: 'neutral',
};

const ROLE_DESCRIPTIONS: Record<AdminRole, string> = {
  SUPER_ADMIN: 'Hər şey, o cümlədən digər adminlərin idarəsi',
  ADMIN: 'İstifadəçi idarəsi xaric hər şey',
  EDITOR: 'Kataloq, məzmun və media',
  SUPPORT: 'Sifarişlər, sorğular və chat',
};

interface NewUserForm {
  firstName: string;
  lastName: string;
  email: string;
  role: AdminRole;
  password: string;
}

const EMPTY_USER: NewUserForm = {
  firstName: '',
  lastName: '',
  email: '',
  role: 'EDITOR',
  password: '',
};

export function UsersPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const currentUser = useAdminAuth((state) => state.user);

  const [form, setForm] = useState<NewUserForm | null>(null);

  const users = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => getPage<AdminUserRow>('/admin/users', { size: 50 }),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

  const create = useMutation({
    mutationFn: (values: NewUserForm) =>
      postData('/admin/users', {
        ...values,
        password: values.password || undefined,
      }),
    onSuccess: () => {
      invalidate();
      setForm(null);
      toast.success('İstifadəçi yaradıldı');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: number; role: AdminRole }) =>
      patchData(`/admin/users/${id}/role`, { role }),
    onSuccess: () => {
      invalidate();
      toast.success('Rol dəyişdirildi. İstifadəçinin sessiyaları bağlandı.');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      patchData(`/admin/users/${id}/status`, { status }),
    onSuccess: () => {
      invalidate();
      toast.success('Status dəyişdirildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const setPassword = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) =>
      putData(`/admin/users/${id}/password`, { password }),
    onSuccess: () => toast.success('Şifrə dəyişdirildi'),
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/users/${id}`),
    onSuccess: () => {
      invalidate();
      toast.success('İstifadəçi silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  return (
    <>
      <PageHeader
        title="İstifadəçilər"
        description="Admin panelə girişi olan hesablar"
        actions={
          <Button icon={<Plus size={15} aria-hidden="true" />} onClick={() => setForm({ ...EMPTY_USER })}>
            Yeni istifadəçi
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <Card bodyClassName="p-0">
          {users.isLoading ? (
            <TableSkeleton rows={5} columns={4} />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-border)] text-xs uppercase tracking-wide text-[var(--color-subtle)]">
                    <th className="px-5 py-3 text-start font-semibold">İstifadəçi</th>
                    <th className="px-5 py-3 text-start font-semibold">Rol</th>
                    <th className="px-5 py-3 text-start font-semibold">Son giriş</th>
                    <th className="px-5 py-3 text-end font-semibold">Əməliyyat</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border)]">
                  {(users.data?.content ?? []).map((user) => {
                    const isSelf = user.id === currentUser?.id;
                    return (
                      <tr key={user.id} className="transition-colors hover:bg-[var(--color-muted)]">
                        <td className="px-5 py-3">
                          <p className="font-medium text-[var(--color-heading)]">
                            {user.firstName} {user.lastName}
                            {isSelf && (
                              <span className="ms-2 text-xs font-normal text-[var(--color-subtle)]">
                                (siz)
                              </span>
                            )}
                          </p>
                          <p className="text-xs text-[var(--color-subtle)]">{user.email}</p>
                        </td>

                        <td className="px-5 py-3">
                          {isSelf ? (
                            <Badge tone={ROLE_TONE[user.role]}>{ROLE_LABELS[user.role]}</Badge>
                          ) : (
                            <Select
                              value={user.role}
                              onChange={(event) =>
                                changeRole.mutate({
                                  id: user.id,
                                  role: event.target.value as AdminRole,
                                })
                              }
                              className="max-w-[150px] py-1.5 text-xs"
                            >
                              {(Object.keys(ROLE_LABELS) as AdminRole[]).map((role) => (
                                <option key={role} value={role}>
                                  {ROLE_LABELS[role]}
                                </option>
                              ))}
                            </Select>
                          )}
                        </td>

                        <td className="px-5 py-3 text-xs text-[var(--color-body)]">
                          {user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '—'}
                          {user.status !== 'ACTIVE' && (
                            <Badge tone="danger" className="ms-2">
                              {user.status === 'LOCKED' ? 'Bloklanıb' : 'Deaktiv'}
                            </Badge>
                          )}
                        </td>

                        <td className="px-5 py-3">
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Şifrə təyin et"
                              onClick={() => {
                                const password = window.prompt(
                                  'Yeni şifrə (ən azı 10 simvol):',
                                );
                                if (password) {
                                  setPassword.mutate({ id: user.id, password });
                                }
                              }}
                              icon={<KeyRound size={14} aria-hidden="true" />}
                            />
                            {!isSelf && (
                              <>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  title={user.status === 'ACTIVE' ? 'Deaktiv et' : 'Aktivləşdir'}
                                  onClick={() =>
                                    changeStatus.mutate({
                                      id: user.id,
                                      status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
                                    })
                                  }
                                  icon={<ShieldCheck size={14} aria-hidden="true" />}
                                />
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  title="Sil"
                                  className="text-[var(--color-danger)]"
                                  onClick={() => {
                                    if (window.confirm(`${user.email} silinsin?`)) {
                                      remove.mutate(user.id);
                                    }
                                  }}
                                  icon={<Trash2 size={14} aria-hidden="true" />}
                                />
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        {form ? (
          <Card
            title="Yeni istifadəçi"
            className="lg:sticky lg:top-24 lg:self-start"
            actions={
              <Button variant="ghost" size="sm" onClick={() => setForm(null)}>
                Bağla
              </Button>
            }
          >
            <div className="space-y-4">
              <Field label="Ad" htmlFor="firstName" required>
                <Input
                  id="firstName"
                  value={form.firstName}
                  onChange={(event) => setForm({ ...form, firstName: event.target.value })}
                />
              </Field>
              <Field label="Soyad" htmlFor="lastName" required>
                <Input
                  id="lastName"
                  value={form.lastName}
                  onChange={(event) => setForm({ ...form, lastName: event.target.value })}
                />
              </Field>
              <Field label="E-poçt" htmlFor="newEmail" required>
                <Input
                  id="newEmail"
                  type="email"
                  value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })}
                />
              </Field>
              <Field label="Rol" htmlFor="newRole" hint={ROLE_DESCRIPTIONS[form.role]}>
                <Select
                  id="newRole"
                  value={form.role}
                  onChange={(event) => setForm({ ...form, role: event.target.value as AdminRole })}
                >
                  {(Object.keys(ROLE_LABELS) as AdminRole[]).map((role) => (
                    <option key={role} value={role}>
                      {ROLE_LABELS[role]}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field
                label="Şifrə"
                htmlFor="newPassword"
                hint="Boş qoyulsa sistem şifrə yaradır və serverin loglarına bir dəfə yazır"
              >
                <Input
                  id="newPassword"
                  type="password"
                  value={form.password}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                  placeholder="Avtomatik"
                />
              </Field>

              <Button
                className="w-full"
                loading={create.isPending}
                disabled={!form.firstName || !form.lastName || !form.email}
                onClick={() => create.mutate(form)}
              >
                Yarat
              </Button>
            </div>
          </Card>
        ) : (
          <Card title="Rollar">
            <dl className="space-y-3 text-sm">
              {(Object.keys(ROLE_LABELS) as AdminRole[]).map((role) => (
                <div key={role}>
                  <dt className="mb-0.5">
                    <Badge tone={ROLE_TONE[role]}>{ROLE_LABELS[role]}</Badge>
                  </dt>
                  <dd className="text-xs text-[var(--color-body)]">{ROLE_DESCRIPTIONS[role]}</dd>
                </div>
              ))}
            </dl>
          </Card>
        )}
      </div>
    </>
  );
}
