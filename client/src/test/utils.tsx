import { ConfigProvider } from '@arco-design/web-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';

import type { MeResponse } from '../api/types';
import { arcoLocale } from '../i18n/arcoLocale';
import { useAuthStore } from '../store/auth';

export function renderWithProviders(ui: ReactElement, route = '/'): RenderResult {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <ConfigProvider locale={arcoLocale}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </QueryClientProvider>
    </ConfigProvider>,
  );
}

/** A signed-in learner with no publisher: the common anonymous baseline. */
export const studentUser: MeResponse = {
  id: 1,
  email: 'student1@exemple.com',
  fullName: 'Camille Apprenante',
  role: 'STUDENT',
  tenantId: null,
  tenantSlug: null,
  tenantType: null,
  mustChangePassword: false,
  hasPassword: true,
};

export const instructorUser: MeResponse = {
  ...studentUser,
  id: 2,
  email: 'formateur@exemple.com',
  fullName: 'Yanis Formateur',
  role: 'INSTRUCTOR',
  tenantId: '11111111-1111-1111-1111-111111111111',
  tenantSlug: 'atelier-lumiere',
  tenantType: 'ORGANIZATION',
};

export const individualAdminUser: MeResponse = {
  ...studentUser,
  id: 3,
  email: 'createur@exemple.com',
  fullName: 'Nadia Créatrice',
  role: 'TENANT_ADMIN',
  tenantId: '22222222-2222-2222-2222-222222222222',
  tenantSlug: 'nadia-studio',
  tenantType: 'INDIVIDUAL',
};

export const organizationAdminUser: MeResponse = {
  ...individualAdminUser,
  id: 4,
  email: 'admin@exemple.com',
  fullName: 'Samir Administrateur',
  tenantType: 'ORGANIZATION',
};

export const superAdminUser: MeResponse = {
  ...studentUser,
  id: 5,
  email: 'admin@sanaa.example',
  fullName: 'Admin Plateforme',
  role: 'SUPER_ADMIN',
};

/** Puts a fake session in the store without going through the network. */
export function setAuthUser(user: MeResponse | null): void {
  useAuthStore.setState({
    accessToken: user ? 'test-token' : null,
    user,
    status: user ? 'authenticated' : 'anonymous',
    sessionStartedAt: null,
  });
}
