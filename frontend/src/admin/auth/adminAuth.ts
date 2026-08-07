import { create } from 'zustand';
import { apiClient, postData, tokenStore } from '@/lib/api';

export type AdminRole = 'SUPER_ADMIN' | 'ADMIN' | 'EDITOR' | 'SUPPORT';

export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: AdminRole;
  locale: string;
  avatarMediaId: number | null;
}

interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: AdminUser;
}

interface AdminAuthState {
  user: AdminUser | null;
  /** Distinguishes "not signed in" from "we have not checked yet". */
  status: 'idle' | 'checking' | 'authenticated' | 'anonymous';
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  setUser: (user: AdminUser) => void;
}

/**
 * Admin session.
 *
 * The access token is held in `tokenStore` (memory only, never localStorage) so an XSS
 * that gets past the CSP cannot read it. Persistence comes from the HttpOnly refresh
 * cookie: on reload, `restore()` exchanges it for a fresh access token.
 */
export const useAdminAuth = create<AdminAuthState>((set) => ({
  user: null,
  status: 'idle',

  async login(email, password) {
    const response = await postData<LoginResponse>('/admin/auth/login', { email, password });
    tokenStore.setAdmin(response.accessToken);
    set({ user: response.user, status: 'authenticated' });
  },

  async logout() {
    try {
      await apiClient.post('/admin/auth/logout');
    } catch {
      // A failed logout call must still clear the client: the user asked to leave.
    }
    tokenStore.setAdmin(null);
    set({ user: null, status: 'anonymous' });
  },

  /**
   * Called once on mount. A missing or expired refresh cookie is the normal
   * "not signed in" path, not an error worth surfacing.
   */
  async restore() {
    set({ status: 'checking' });
    try {
      const response = await postData<LoginResponse>('/admin/auth/refresh');
      tokenStore.setAdmin(response.accessToken);
      set({ user: response.user, status: 'authenticated' });
    } catch {
      tokenStore.setAdmin(null);
      set({ user: null, status: 'anonymous' });
    }
  },

  setUser(user) {
    set({ user });
  },
}));

// ---------------------------------------------------------------------------
// Permissions
//
// Mirrors the server's @PreAuthorize rules. This only decides what to *render* —
// the API enforces the same rules independently, so a hidden button is a courtesy,
// never a security control.
// ---------------------------------------------------------------------------

export type Capability =
  | 'catalog.manage'
  | 'discounts.manage'
  | 'orders.manage'
  | 'orders.refund'
  | 'content.manage'
  | 'media.manage'
  | 'customers.view'
  | 'settings.manage'
  | 'users.manage'
  | 'chat.handle';

const ROLE_CAPABILITIES: Record<AdminRole, Capability[]> = {
  SUPER_ADMIN: [
    'catalog.manage', 'discounts.manage', 'orders.manage', 'orders.refund',
    'content.manage', 'media.manage', 'customers.view', 'settings.manage',
    'users.manage', 'chat.handle',
  ],
  ADMIN: [
    'catalog.manage', 'discounts.manage', 'orders.manage', 'orders.refund',
    'content.manage', 'media.manage', 'customers.view', 'settings.manage',
    'chat.handle',
  ],
  EDITOR: ['catalog.manage', 'discounts.manage', 'content.manage', 'media.manage'],
  SUPPORT: ['orders.manage', 'customers.view', 'chat.handle'],
};

export function useCan(capability: Capability): boolean {
  const role = useAdminAuth((state) => state.user?.role);
  return role ? ROLE_CAPABILITIES[role].includes(capability) : false;
}

export function hasCapability(role: AdminRole | undefined, capability: Capability): boolean {
  return role ? ROLE_CAPABILITIES[role].includes(capability) : false;
}

export const ROLE_LABELS: Record<AdminRole, string> = {
  SUPER_ADMIN: 'Super admin',
  ADMIN: 'Admin',
  EDITOR: 'Redaktor',
  SUPPORT: 'Dəstək',
};
