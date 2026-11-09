import axios, {
  type AxiosAdapter,
  type AxiosInstance,
  isAxiosError,
} from 'axios';

import { toApiError } from './errors';
import type { LoginResponse, ProblemDetail } from './types';
import { useAuthStore } from '../store/auth';
import { tenantSearch } from '../tenant/navigation';

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** Internal marker: the request was already retried after a refresh. */
    _retry?: boolean;
  }
}

const AUTH_ENDPOINTS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
];

interface HttpClientOptions {
  adapter?: AxiosAdapter;
  /** Returns a new access token. Injected in tests. */
  refresh?: () => Promise<string>;
  onSessionExpired?: () => void;
}

async function defaultRefresh(): Promise<string> {
  // Bypasses the instance on purpose: a failing refresh must never trigger
  // another refresh.
  const response = await axios.post<LoginResponse>('/api/auth/refresh', null, {
    withCredentials: true,
  });
  return response.data.accessToken;
}

function defaultSessionExpired(): void {
  useAuthStore.getState().clear();
  if (window.location.pathname !== '/login') {
    window.location.assign(`/login${tenantSearch(window.location.search)}`);
  }
}

function isAuthEndpoint(url: string | undefined): boolean {
  return url !== undefined && AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint));
}

/**
 * Builds the shared HTTP client.
 *
 * WHY a single shared refresh promise: when three requests get a 401 at the
 * same time, they must rotate the refresh cookie once. Parallel refreshes
 * would present the same rotated token and revoke the whole session.
 */
export function createHttpClient(options: HttpClientOptions = {}): AxiosInstance {
  const instance = axios.create({ withCredentials: true });
  if (options.adapter) {
    instance.defaults.adapter = options.adapter;
  }

  const refresh = options.refresh ?? defaultRefresh;
  const onSessionExpired = options.onSessionExpired ?? defaultSessionExpired;
  let refreshPromise: Promise<string> | null = null;

  instance.interceptors.request.use((config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (!isAxiosError<ProblemDetail>(error)) {
        throw toApiError(error);
      }
      const config = error.config;
      const status = error.response?.status;

      if (status === 401 && config && !config._retry && !isAuthEndpoint(config.url)) {
        config._retry = true;
        try {
          if (!refreshPromise) {
            refreshPromise = refresh().finally(() => {
              refreshPromise = null;
            });
          }
          const token = await refreshPromise;
          useAuthStore.getState().setAccessToken(token);
          config.headers.set('Authorization', `Bearer ${token}`);
          // One retry only: the _retry marker stops a second refresh.
          return instance.request(config);
        } catch {
          onSessionExpired();
        }
      }

      throw toApiError(error);
    },
  );

  return instance;
}

export const http = createHttpClient();
