import { create } from 'zustand';
import { apiClient, getCartSessionId, postData, putData, tokenStore } from '@/lib/api';
import type { CustomerAuthResponse, CustomerProfile } from '@/types/api';

export interface RegisterPayload {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
  companyName?: string;
  marketingOptIn?: boolean;
  termsAccepted: boolean;
  referralCode?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface UpdateProfilePayload {
  firstName: string;
  lastName: string;
  phone?: string;
  companyName?: string;
  taxNumber?: string;
  customerType?: CustomerProfile['customerType'];
  locale?: string;
  marketingOptIn?: boolean;
}

interface CustomerAuthState {
  customer: CustomerProfile | null;
  /** Distinguishes "not signed in" from "we have not checked yet". */
  status: 'idle' | 'checking' | 'authenticated' | 'anonymous';
  register: (payload: RegisterPayload) => Promise<void>;
  login: (payload: LoginPayload) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  updateProfile: (payload: UpdateProfilePayload) => Promise<void>;
}

/**
 * Customer (storefront) session — separate audience from the admin one.
 *
 * The access token lives in `tokenStore` (memory only). A guest cart is folded into the
 * account on register/login by sending `cartSessionId`: the server merges it, so nothing
 * further is needed here beyond letting the cart query refetch.
 */
export const useCustomerAuth = create<CustomerAuthState>((set) => ({
  customer: null,
  status: 'idle',

  async register(payload) {
    const response = await postData<CustomerAuthResponse>('/customer/auth/register', {
      ...payload,
      cartSessionId: getCartSessionId(),
    });
    tokenStore.setCustomer(response.accessToken);
    set({ customer: response.customer, status: 'authenticated' });
  },

  async login(payload) {
    const response = await postData<CustomerAuthResponse>('/customer/auth/login', {
      ...payload,
      cartSessionId: getCartSessionId(),
    });
    tokenStore.setCustomer(response.accessToken);
    set({ customer: response.customer, status: 'authenticated' });
  },

  async logout() {
    try {
      await apiClient.post('/customer/auth/logout');
    } catch {
      // A failed logout call must still clear the client: the user asked to leave.
    }
    tokenStore.setCustomer(null);
    set({ customer: null, status: 'anonymous' });
  },

  /**
   * Called on demand — the storefront should not spend a round trip checking every
   * anonymous visitor's session, so this only runs when a page actually needs to know
   * (the account area, or the login/register forms guarding against a signed-in visitor).
   */
  async restore() {
    set({ status: 'checking' });
    try {
      const response = await postData<CustomerAuthResponse>('/customer/auth/refresh');
      tokenStore.setCustomer(response.accessToken);
      set({ customer: response.customer, status: 'authenticated' });
    } catch {
      tokenStore.setCustomer(null);
      set({ customer: null, status: 'anonymous' });
    }
  },

  async updateProfile(payload) {
    const profile = await putData<CustomerProfile>('/customer/auth/me', payload);
    set({ customer: profile });
  },
}));
