import { sanitizeRedirect } from './url';

const REDIRECT_KEY = 'sanaa.google.redirect';

/** Keeps the intended internal page in this tab while Google owns the top-level navigation. */
export function rememberGoogleRedirect(value: string | null | undefined): void {
  try {
    window.sessionStorage.setItem(REDIRECT_KEY, sanitizeRedirect(value, '/me'));
  } catch {
    // Storage can be unavailable in private mode; /me remains the safe fallback.
  }
}

/** Reads once so a later login cannot inherit a stale destination. */
export function consumeGoogleRedirect(): string {
  try {
    const value = window.sessionStorage.getItem(REDIRECT_KEY);
    window.sessionStorage.removeItem(REDIRECT_KEY);
    return sanitizeRedirect(value, '/me');
  } catch {
    return '/me';
  }
}
