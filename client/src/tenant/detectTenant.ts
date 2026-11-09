/**
 * Tenant slug used for branding and for the tenantSlug sent on login/register.
 * Display only: authorization always uses the JWT.
 *
 * Priority:
 * 1. first hostname label when the host ends with .localhost or the base domain;
 * 2. ?tenant= query parameter;
 * 3. VITE_DEFAULT_TENANT.
 *
 * With no source at all the application must show the organisation picker and
 * must not call the branding API.
 */
const SLUG_PATTERN = /^[a-z0-9-]{3,63}$/;

export type TenantSource = 'host' | 'query' | 'default';

export interface TenantDetection {
  slug: string | null;
  source: TenantSource | null;
}

export interface TenantDetectionInput {
  hostname: string;
  search: string;
  defaultTenant?: string;
  baseDomain?: string;
}

export function normalizeTenantSlug(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const slug = value.trim().toLowerCase();
  return SLUG_PATTERN.test(slug) ? slug : null;
}

export function detectTenant(input: TenantDetectionInput): TenantDetection {
  const fromHost = tenantFromHostname(input.hostname, input.baseDomain);
  if (fromHost) {
    return { slug: fromHost, source: 'host' };
  }

  const fromQuery = normalizeTenantSlug(new URLSearchParams(input.search).get('tenant'));
  if (fromQuery) {
    return { slug: fromQuery, source: 'query' };
  }

  const fromDefault = normalizeTenantSlug(input.defaultTenant);
  if (fromDefault) {
    return { slug: fromDefault, source: 'default' };
  }

  return { slug: null, source: null };
}

export function detectTenantSlug(input: TenantDetectionInput): string | null {
  return detectTenant(input).slug;
}

function tenantFromHostname(rawHostname: string, rawBaseDomain?: string): string | null {
  const hostname = stripPort(rawHostname).toLowerCase();
  if (!hostname || hostname === 'localhost') {
    return null;
  }

  const baseDomain = normalizeBaseDomain(rawBaseDomain);
  let suffix: string | null = null;
  if (hostname.endsWith('.localhost')) {
    suffix = '.localhost';
  } else if (baseDomain && baseDomain !== 'localhost' && hostname.endsWith(`.${baseDomain}`)) {
    suffix = `.${baseDomain}`;
  }
  if (!suffix) {
    return null;
  }

  // "first hostname label": atlas.localhost -> atlas, a.b.example.com -> a.
  const withoutSuffix = hostname.slice(0, -suffix.length);
  const firstLabel = withoutSuffix.split('.')[0];
  return normalizeTenantSlug(firstLabel);
}

function normalizeBaseDomain(rawBaseDomain?: string): string | null {
  if (!rawBaseDomain) {
    return null;
  }
  return rawBaseDomain.trim().toLowerCase().replace(/^\./, '');
}

function stripPort(hostname: string): string {
  const trimmed = hostname.trim();
  if (trimmed.startsWith('[')) {
    return trimmed;
  }
  const colon = trimmed.indexOf(':');
  return colon >= 0 ? trimmed.slice(0, colon) : trimmed;
}

/** Reads the current browser location. Kept separate so tests stay pure. */
export function currentTenant(search = window.location.search): TenantDetection {
  return detectTenant({
    hostname: window.location.hostname,
    search,
    defaultTenant: import.meta.env.VITE_DEFAULT_TENANT,
    baseDomain: import.meta.env.VITE_BASE_DOMAIN,
  });
}
