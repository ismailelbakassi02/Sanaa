import { create } from 'zustand';

import type { MeResponse } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthState {
  /** Memory only. Never written to localStorage or sessionStorage. */
  accessToken: string | null;
  user: MeResponse | null;
  status: AuthStatus;
  sessionStartedAt: string | null;
  setAccessToken: (token: string) => void;
  setUser: (user: MeResponse) => void;
  setSession: (token: string, user: MeResponse) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  status: 'loading',
  sessionStartedAt: null,
  setAccessToken: (token) => set({ accessToken: token }),
  setUser: (user) => set({ user, status: 'authenticated' }),
  setSession: (token, user) =>
    set({
      accessToken: token,
      user,
      status: 'authenticated',
      sessionStartedAt: new Date().toISOString(),
    }),
  clear: () =>
    set({
      accessToken: null,
      user: null,
      status: 'anonymous',
      sessionStartedAt: null,
    }),
}));
