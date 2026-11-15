/**
 * Publisher slugs. The rules mirror auth-service SlugRules: the slug becomes a
 * public URL segment, so it is lowercase ASCII, 3 to 63 characters, cannot
 * start or end with a hyphen, and cannot impersonate a platform route.
 */
export const SLUG_PATTERN = /^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$/;

const RESERVED = new Set([
  'platform',
  'www',
  'api',
  'admin',
  'app',
  'mail',
  'static',
  'auth',
  'support',
  'help',
  'blog',
  'docs',
  'status',
  'cdn',
  'assets',
  'login',
  'register',
  'dashboard',
  'billing',
  'sanaa',
  'studio',
  'learn',
  'courses',
  'publishers',
  'instructors',
  'public',
]);

export function normalizeSlug(value: string): string {
  return value.trim().toLowerCase();
}

/** Turns a display name into a candidate slug. Returns '' when nothing is left. */
export function slugFromName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 63)
    .replace(/-+$/g, '');
}

export function isReservedSlug(slug: string): boolean {
  return RESERVED.has(normalizeSlug(slug));
}

export function isValidSlug(slug: string): boolean {
  const value = normalizeSlug(slug);
  return SLUG_PATTERN.test(value) && !value.includes('xn--') && !isReservedSlug(value);
}
