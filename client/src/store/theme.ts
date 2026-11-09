import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'sanaa.theme';

function readStoredTheme(): ThemeMode {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function applyTheme(mode: ThemeMode): void {
  if (mode === 'dark') {
    document.body.setAttribute('arco-theme', 'dark');
  } else {
    document.body.removeAttribute('arco-theme');
  }
}

interface ThemeState {
  mode: ThemeMode;
  toggle: () => void;
}

export const useThemeStore = create<ThemeState>()((set, get) => ({
  mode: readStoredTheme(),
  toggle: () => {
    const mode: ThemeMode = get().mode === 'dark' ? 'light' : 'dark';
    applyTheme(mode);
    try {
      // The theme is the only preference persisted, and never a token.
      window.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Private browsing can reject storage writes; the session still works.
    }
    set({ mode });
  },
}));
