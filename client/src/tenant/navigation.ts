import { useLocation } from 'react-router-dom';

import { normalizeTenantSlug } from './detectTenant';

/**
 * Returns the tenant query string to append to an in-app path.
 *
 * WHY: when the organisation came from ?tenant=, a redirect such as login to
 * home must keep it so a reload on /?tenant=atlas still resolves the tenant.
 */
export function tenantSearch(search: string): string {
  const slug = normalizeTenantSlug(new URLSearchParams(search).get('tenant'));
  return slug ? `?tenant=${slug}` : '';
}

export function useTenantSearch(): string {
  return tenantSearch(useLocation().search);
}
