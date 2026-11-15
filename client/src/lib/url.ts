/**
 * Profile and publisher links are rendered as clickable anchors on public
 * pages. Only absolute HTTPS URLs are accepted, and user info is rejected so a
 * link can never look like `https://trusted.example@evil.example`.
 */
export const URL_MAX_LENGTH = 200;

export type UrlIssue = 'invalid' | 'tooLong' | null;

export function isSafeHttpsUrl(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > URL_MAX_LENGTH) {
    return false;
  }
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return false;
  }
  return (
    url.protocol === 'https:' &&
    url.hostname.length > 0 &&
    url.username === '' &&
    url.password === ''
  );
}

/** Blank is allowed: every social link is optional. */
export function urlIssue(value: string | undefined): UrlIssue {
  if (value === undefined || value.trim() === '') {
    return null;
  }
  if (value.trim().length > URL_MAX_LENGTH) {
    return 'tooLong';
  }
  return isSafeHttpsUrl(value) ? null : 'invalid';
}

/** A value is only handed to href when it passes every check. */
export function safeHref(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  return isSafeHttpsUrl(value) ? value.trim() : null;
}
