import { ConfigProvider } from '@arco-design/web-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';

import type { MeResponse } from '../api/types';
import { arcoLocale } from '../i18n/arcoLocale';

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

export const studentUser: MeResponse = {
  id: 1,
  email: 'student1@atlas.local',
  fullName: 'Atlas Student One',
  role: 'STUDENT',
  tenantId: '11111111-1111-1111-1111-111111111111',
  tenantSlug: 'atlas',
};
