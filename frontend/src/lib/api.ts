import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiErrorBody, DataResponse, ErrorCode, PageResponse } from '@/types/api';

const API_URL = import.meta.env.VITE_API_URL ?? '/api';

const CART_SESSION_KEY = 'ctl.cartSession';
const LANGUAGE_KEY = 'ctl.language';
const VISITOR_TOKEN_KEY = 'ctl.visitorToken';

/**
 * Typed wrapper around a failed request. Components switch on `code`, never on the
 * message text, so wording can change server-side without breaking the UI.
 */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody, status: number) {
    super(body.message);
    this.name = 'ApiError';
    this.code = body.code;
    this.status = status;
    this.fieldErrors = body.errors ?? {};
  }

  static network(): ApiError {
    return new ApiError(
      {
        timestamp: new Date().toISOString(),
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'Serverlə əlaqə qurulmadı. İnternet bağlantınızı yoxlayın.',
      },
      0,
    );
  }

  is(...codes: ErrorCode[]): boolean {
    return codes.includes(this.code);
  }
}

// ---------------------------------------------------------------------------
// Access token — kept in memory only.
//
// The refresh token lives in an HttpOnly cookie the JS cannot read; the access
// token is deliberately NOT persisted to localStorage, which keeps it out of
// reach of any XSS that gets past the CSP. A page reload silently re-acquires
// one through /auth/refresh.
// ---------------------------------------------------------------------------

let adminAccessToken: string | null = null;
let customerAccessToken: string | null = null;

export const tokenStore = {
  setAdmin(token: string | null) {
    adminAccessToken = token;
  },
  setCustomer(token: string | null) {
    customerAccessToken = token;
  },
  getAdmin: () => adminAccessToken,
  getCustomer: () => customerAccessToken,
  clear() {
    adminAccessToken = null;
    customerAccessToken = null;
  },
};

/** Anonymous cart identity. Generated once and kept in localStorage. */
export function getCartSessionId(): string {
  let sessionId = localStorage.getItem(CART_SESSION_KEY);
  if (!sessionId) {
    sessionId = crypto.randomUUID();
    localStorage.setItem(CART_SESSION_KEY, sessionId);
  }
  return sessionId;
}

export function resetCartSession(): void {
  localStorage.removeItem(CART_SESSION_KEY);
}

/**
 * The visitor's chat identity.
 *
 * Generated once per browser and kept in localStorage. It is both the identity that
 * links a returning visitor to their thread and the secret that authorises their STOMP
 * subscription, so it is created with `crypto.randomUUID`, never a timestamp or counter.
 */
export function getVisitorToken(): string {
  let token = localStorage.getItem(VISITOR_TOKEN_KEY);
  if (!token || token.length < 32) {
    token = crypto.randomUUID();
    localStorage.setItem(VISITOR_TOKEN_KEY, token);
  }
  return token;
}

export function getLanguage(): string {
  return localStorage.getItem(LANGUAGE_KEY) ?? 'az';
}

export function setLanguage(code: string): void {
  localStorage.setItem(LANGUAGE_KEY, code);
  document.documentElement.lang = code;
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

function createClient(): AxiosInstance {
  const client = axios.create({
    baseURL: API_URL,
    timeout: 30_000,
    // Required so the refresh cookie travels with the request.
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' },
  });

  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    // Admin routes get the admin token, everything else the customer token.
    // Sending the wrong audience would simply be rejected by the server.
    const url = config.url ?? '';
    const token = url.startsWith('/admin') ? adminAccessToken : customerAccessToken;
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }

    config.headers.set('Accept-Language', getLanguage());

    // Guest cart identity, needed by /public/cart and /public/checkout.
    if (url.includes('/cart') || url.includes('/checkout')) {
      config.headers.set('X-Cart-Session', getCartSessionId());
    }

    // The chat and product-presence endpoints require this to identify the visitor —
    // without it those calls are rejected outright with a missing-header 400.
    if (url.includes('/chat') || url.includes('/presence')) {
      config.headers.set('X-Visitor-Token', getVisitorToken());
    }

    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiErrorBody>) => {
      if (!error.response) {
        return Promise.reject(ApiError.network());
      }

      const { status, data } = error.response;
      const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean };

      // A 401 from an expired access token is recoverable: refresh once and replay.
      // `_retried` stops an infinite loop when the refresh itself 401s.
      if (
        status === 401 &&
        data?.code === 'TOKEN_EXPIRED' &&
        original &&
        !original._retried &&
        !original.url?.includes('/auth/refresh')
      ) {
        original._retried = true;
        const isAdmin = original.url?.startsWith('/admin') ?? false;
        const refreshed = await refreshToken(isAdmin);
        if (refreshed) {
          return client(original);
        }
      }

      return Promise.reject(
        new ApiError(
          data ?? {
            timestamp: new Date().toISOString(),
            status,
            code: 'INTERNAL_ERROR',
            message: 'Gözlənilməz xəta baş verdi',
          },
          status,
        ),
      );
    },
  );

  return client;
}

export const apiClient = createClient();

/**
 * Concurrent 401s must not fire several refresh calls — the first one wins and the
 * rest await the same promise. Without this, a page that loads six widgets at once
 * would rotate the refresh token six times and invalidate itself.
 */
let refreshPromise: Promise<boolean> | null = null;

async function refreshToken(isAdmin: boolean): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const endpoint = isAdmin ? '/admin/auth/refresh' : '/customer/auth/refresh';
      const { data } = await axios.post<DataResponse<{ accessToken: string }>>(
        `${API_URL}${endpoint}`,
        {},
        { withCredentials: true },
      );
      if (isAdmin) {
        tokenStore.setAdmin(data.data.accessToken);
      } else {
        tokenStore.setCustomer(data.data.accessToken);
      }
      return true;
    } catch {
      if (isAdmin) {
        tokenStore.setAdmin(null);
      } else {
        tokenStore.setCustomer(null);
      }
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export { refreshToken };

// ---------------------------------------------------------------------------
// Thin helpers that unwrap the response envelopes
// ---------------------------------------------------------------------------

export async function getData<T>(url: string, params?: unknown): Promise<T> {
  const { data } = await apiClient.get<DataResponse<T>>(url, { params });
  return data.data;
}

export async function getPage<T>(url: string, params?: unknown): Promise<PageResponse<T>> {
  const { data } = await apiClient.get<PageResponse<T>>(url, { params });
  return data;
}

export async function postData<T>(url: string, body?: unknown): Promise<T> {
  const { data } = await apiClient.post<DataResponse<T>>(url, body);
  return data.data;
}

export async function putData<T>(url: string, body?: unknown): Promise<T> {
  const { data } = await apiClient.put<DataResponse<T>>(url, body);
  return data.data;
}

export async function patchData<T>(url: string, body?: unknown): Promise<T> {
  const { data } = await apiClient.patch<DataResponse<T>>(url, body);
  return data.data;
}

export async function deleteRequest(url: string): Promise<void> {
  await apiClient.delete(url);
}

/** For 204 endpoints where there is no envelope to unwrap. */
export async function postVoid(url: string, body?: unknown): Promise<void> {
  await apiClient.post(url, body);
}
