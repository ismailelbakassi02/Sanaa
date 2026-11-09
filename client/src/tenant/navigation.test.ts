import { describe, expect, it } from 'vitest';

import { tenantSearch } from './navigation';

describe('tenantSearch', () => {
  it('keeps and normalises the tenant parameter', () => {
    expect(tenantSearch('?tenant=%20ATLAS%20')).toBe('?tenant=atlas');
  });

  it('drops an invalid tenant parameter', () => {
    expect(tenantSearch('?tenant=x')).toBe('');
  });

  it('returns an empty suffix without a tenant parameter', () => {
    expect(tenantSearch('?foo=bar')).toBe('');
  });
});
