import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchMe, login } from '../api/auth';
import { ApiError } from '../api/errors';
import type { BrandingResponse } from '../api/types';
import { fr } from '../i18n/fr';
import { LoginPage } from './LoginPage';
import { BrandingContext } from '../tenant/BrandingContext';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, studentUser } from '../test/utils';

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  restoreSession: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  fetchBranding: vi.fn(),
}));

const loginMock = vi.mocked(login);
const fetchMeMock = vi.mocked(fetchMe);

const branding: BrandingResponse = {
  name: 'Atlas Academy',
  primaryColor: '#00B42A',
  logoUrl: null,
  registrationEnabled: true,
};

function HomeProbe() {
  const location = useLocation();
  return <div>{`page-accueil ${location.search}`}</div>;
}

function renderLoginPage(
  route = '/login',
  brandingOverrides: Partial<BrandingResponse> = {},
) {
  return renderWithProviders(
    <BrandingContext.Provider
      value={{
        slug: 'atlas',
        branding: { ...branding, ...brandingOverrides },
      }}
    >
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<div>page-inscription</div>} />
        <Route path="/" element={<HomeProbe />} />
      </Routes>
    </BrandingContext.Provider>,
    route,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
  loginMock.mockReset();
  fetchMeMock.mockReset();
});

describe('LoginPage', () => {
  it('renders the French labels of the login form', () => {
    renderLoginPage();

    expect(screen.getByRole('heading', { name: 'Connexion', level: 1 })).toBeInTheDocument();
    expect(screen.getByText(fr.auth.login.emailLabel)).toBeInTheDocument();
    expect(screen.getByText(fr.auth.login.passwordLabel)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(fr.auth.login.emailPlaceholder)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
    expect(screen.getByText('Atlas Academy')).toBeInTheDocument();
  });

  it('shows the French message when the login returns 401', async () => {
    loginMock.mockRejectedValue(
      new ApiError(401, { title: 'Unauthorized', detail: 'Invalid tenant, email or password.' }),
    );
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(
      screen.getByPlaceholderText(fr.auth.login.emailPlaceholder),
      'student1@atlas.local',
    );
    await user.type(
      screen.getByPlaceholderText(fr.auth.login.passwordPlaceholder),
      'mot-de-passe',
    );
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByText(fr.errors.invalidCredentials)).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith({
      tenantSlug: 'atlas',
      email: 'student1@atlas.local',
      password: 'mot-de-passe',
    });
  });

  it('keeps ?tenant= when redirecting to the home page after login', async () => {
    loginMock.mockResolvedValue({ accessToken: 'token', expiresIn: 900 });
    fetchMeMock.mockResolvedValue(studentUser);
    const user = userEvent.setup();
    renderLoginPage('/login?tenant=atlas');

    await user.type(
      screen.getByPlaceholderText(fr.auth.login.emailPlaceholder),
      'student1@atlas.local',
    );
    await user.type(screen.getByPlaceholderText(fr.auth.login.passwordPlaceholder), 'secret');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByText('page-accueil ?tenant=atlas')).toBeInTheDocument();
  });

  it('hides account creation when registration is disabled', () => {
    renderLoginPage('/login?tenant=platform', { registrationEnabled: false });

    expect(
      screen.queryByRole('link', { name: fr.auth.login.createAccount }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(fr.auth.login.registrationDisabled)).toBeInTheDocument();
  });

  it('keeps ?tenant= on the account creation link', () => {
    renderLoginPage('/login?tenant=atlas');

    expect(screen.getByRole('link', { name: fr.auth.login.createAccount })).toHaveAttribute(
      'href',
      '/register?tenant=atlas',
    );
  });
});
