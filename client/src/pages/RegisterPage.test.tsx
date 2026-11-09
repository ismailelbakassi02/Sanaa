import { screen } from '@testing-library/react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BrandingResponse } from '../api/types';
import { useAuthStore } from '../store/auth';
import { BrandingContext } from '../tenant/BrandingContext';
import { renderWithProviders } from '../test/utils';
import { RegisterPage } from './RegisterPage';

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  restoreSession: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  fetchBranding: vi.fn(),
}));

const branding: BrandingResponse = {
  name: 'Platform',
  primaryColor: '#165DFF',
  logoUrl: null,
  registrationEnabled: false,
};

function LoginProbe() {
  const location = useLocation();
  return <div>{`page-connexion ${location.search}`}</div>;
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
});

describe('RegisterPage', () => {
  it('redirects to login and keeps ?tenant= when registration is disabled', () => {
    renderWithProviders(
      <BrandingContext.Provider value={{ slug: 'platform', branding }}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </BrandingContext.Provider>,
      '/register?tenant=platform',
    );

    expect(screen.getByText('page-connexion ?tenant=platform')).toBeInTheDocument();
  });
});
