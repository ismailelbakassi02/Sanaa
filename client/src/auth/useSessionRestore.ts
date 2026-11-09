import { useEffect } from 'react';

import { fetchMe, restoreSession } from '../api/auth';
import { useAuthStore } from '../store/auth';

/**
 * Restores the session once per tab: POST /api/auth/refresh uses the HttpOnly
 * cookie, then GET /api/auth/me fills the in-memory user. StrictMode runs the
 * effect twice in development, so the in-flight promise is shared.
 */
let restorePromise: Promise<void> | null = null;

export function useSessionRestore(): void {
  useEffect(() => {
    if (useAuthStore.getState().status !== 'loading') {
      return;
    }
    if (!restorePromise) {
      restorePromise = (async () => {
        try {
          const token = await restoreSession();
          useAuthStore.getState().setAccessToken(token);
          const user = await fetchMe();
          useAuthStore.getState().setSession(token, user);
        } catch {
          useAuthStore.getState().clear();
        } finally {
          restorePromise = null;
        }
      })();
    }
  }, []);
}
