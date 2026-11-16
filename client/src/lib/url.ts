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

/**
 * Only an internal absolute path may be used as a post-login target.
 *
 * WHY not just `startsWith('/')`: `//evil.example` and `/\evil.example` are
 * protocol-relative or backslash-normalized addresses that the browser turns
 * into another origin. A control character can hide the real target too, so
 * the value is parsed against a fixed base and its origin must not change.
 */
export function sanitizeRedirect(
  value: string | null | undefined,
  fallback = '/',
): string {
  if (!value) {
    return fallback;
  }
  const candidate = value.trim();
  if (!candidate.startsWith('/')) {
    return fallback;
  }
  if (candidate.startsWith('//') || candidate.startsWith('/\\')) {
    return fallback;
  }
  for (const character of Array.from(candidate)) {
    const code = character.codePointAt(0) ?? 0;
    if (code <= 0x1f || code === 0x7f) {
      return fallback;
    }
  }
  try {
    const base = 'https://sanaa.invalid';
    const parsed = new URL(candidate, base);
    if (parsed.origin !== base) {
      return fallback;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}
