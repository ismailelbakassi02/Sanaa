import { afterEach, describe, expect, it, vi } from 'vitest';

import { currentTenant, detectTenant, detectTenantSlug } from './detectTenant';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('currentTenant', () => {
  it('returns no tenant when no host, query or default provides one', () => {
    vi.stubEnv('VITE_DEFAULT_TENANT', '');
    vi.stubEnv('VITE_BASE_DOMAIN', 'localhost');
    expect(window.location.hostname).toBe('localhost');
    expect(currentTenant('')).toEqual({ slug: null, source: null });
  });
});

describe('detectTenant', () => {
  it('reports the hostname as the source', () => {
    expect(
      detectTenant({ hostname: 'atlas.localhost', search: '?tenant=acme', defaultTenant: 'acme' }),
    ).toEqual({ slug: 'atlas', source: 'host' });
  });

  it('reports the query parameter as the source', () => {
    expect(
      detectTenant({ hostname: 'localhost', search: '?tenant=acme', defaultTenant: 'atlas' }),
    ).toEqual({ slug: 'acme', source: 'query' });
  });

  it('reports the default tenant as the source', () => {
    expect(detectTenant({ hostname: 'localhost', search: '', defaultTenant: 'atlas' })).toEqual({
      slug: 'atlas',
      source: 'default',
    });
  });

  it('reports no source when everything is missing', () => {
    expect(detectTenant({ hostname: 'localhost', search: '' })).toEqual({
      slug: null,
      source: null,
    });
  });
});

describe('detectTenantSlug', () => {
  it('uses the default tenant on plain localhost', () => {
    expect(
      detectTenantSlug({
        hostname: 'localhost',
        search: '',
        defaultTenant: 'atlas',
        baseDomain: 'localhost',
      }),
    ).toBe('atlas');
  });

  it('reads the subdomain of a .localhost host', () => {
    expect(
      detectTenantSlug({
        hostname: 'atlas.localhost',
        search: '',
        defaultTenant: 'acme',
      }),
    ).toBe('atlas');
  });

  it('ignores the port of a .localhost host', () => {
    expect(
      detectTenantSlug({
        hostname: 'atlas.localhost:5173',
        search: '',
        defaultTenant: 'acme',
      }),
    ).toBe('atlas');
  });

  it('keeps acme distinct from atlas', () => {
    expect(
      detectTenantSlug({
        hostname: 'acme.localhost',
        search: '',
        defaultTenant: 'atlas',
      }),
    ).toBe('acme');
  });

  it('reads the base domain configured through VITE_BASE_DOMAIN', () => {
    expect(
      detectTenantSlug({
        hostname: 'atlas.sanaa.example',
        search: '',
        defaultTenant: 'acme',
        baseDomain: 'sanaa.example',
      }),
    ).toBe('atlas');
  });

  it('uses the first label for a nested host', () => {
    expect(
      detectTenantSlug({
        hostname: 'foo.bar.localhost',
        search: '',
        defaultTenant: 'acme',
      }),
    ).toBe('foo');
  });

  it('falls back to ?tenant= when the host has no label', () => {
    expect(
      detectTenantSlug({
        hostname: 'localhost',
        search: '?tenant=acme',
        defaultTenant: 'atlas',
      }),
    ).toBe('acme');
  });

  it('normalizes the ?tenant= value', () => {
    expect(
      detectTenantSlug({
        hostname: 'localhost',
        search: '?tenant=%20ACME%20',
        defaultTenant: 'atlas',
      }),
    ).toBe('acme');
  });

  it('prefers the host over the query parameter', () => {
    expect(
      detectTenantSlug({
        hostname: 'acme.localhost',
        search: '?tenant=atlas',
        defaultTenant: 'atlas',
      }),
    ).toBe('acme');
  });

  it('falls back to the default tenant when everything else is missing', () => {
    expect(
      detectTenantSlug({
        hostname: '127.0.0.1',
        search: '',
        defaultTenant: 'atlas',
      }),
    ).toBe('atlas');
  });

  it('ignores an invalid host label and uses the default', () => {
    expect(
      detectTenantSlug({
        hostname: 'atlas_x.localhost',
        search: '',
        defaultTenant: 'acme',
      }),
    ).toBe('acme');
  });

  it('returns null when no source is available', () => {
    expect(
      detectTenantSlug({
        hostname: 'localhost',
        search: '',
      }),
    ).toBeNull();
  });
});
