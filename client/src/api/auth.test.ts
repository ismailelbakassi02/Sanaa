import { describe, expect, it, vi } from 'vitest';

import { fetchMe } from './auth';
import { http } from './http';
import type { MeResponse } from './types';

vi.mock('./http', () => ({
  http: {
    get: vi.fn(),
  },
}));

const getMock = vi.mocked(http.get);

function respondWith(me: Partial<MeResponse>): void {
  getMock.mockResolvedValue({ data: me } as never);
}

describe('fetchMe', () => {
  it('fills the tenant fields the auth service omits for a learner with null', async () => {
    respondWith({
      id: 6,
      email: 'amine@exemple.com',
      fullName: 'Amine Tazi',
      role: 'STUDENT',
      mustChangePassword: false,
    });

    await expect(fetchMe()).resolves.toEqual({
      id: 6,
      email: 'amine@exemple.com',
      fullName: 'Amine Tazi',
      role: 'STUDENT',
      tenantId: null,
      tenantSlug: null,
      tenantType: null,
      mustChangePassword: false,
    });
  });

  it('keeps the publisher of a studio administrator', async () => {
    respondWith({
      id: 2,
      email: 'admin@atlas.local',
      fullName: 'Atlas Admin',
      role: 'TENANT_ADMIN',
      tenantId: '11111111-1111-1111-1111-111111111111',
      tenantSlug: 'atlas',
      tenantType: 'ORGANIZATION',
      mustChangePassword: false,
    });

    await expect(fetchMe()).resolves.toMatchObject({
      tenantId: '11111111-1111-1111-1111-111111111111',
      tenantSlug: 'atlas',
      tenantType: 'ORGANIZATION',
    });
  });

  it('defaults a missing mustChangePassword flag to false rather than undefined', async () => {
    respondWith({
      id: 1,
      email: 'camille@exemple.com',
      fullName: 'Camille',
      role: 'STUDENT',
    });

    await expect(fetchMe()).resolves.toMatchObject({ mustChangePassword: false });
  });
});
