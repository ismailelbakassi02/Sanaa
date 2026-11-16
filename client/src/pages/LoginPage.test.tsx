import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchGoogleOAuthStatus, fetchMe, login } from '../api/auth';
import { ApiError } from '../api/errors';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { LoginPage } from './LoginPage';

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  restoreSession: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  fetchGoogleOAuthStatus: vi.fn(),
}));

const loginMock = vi.mocked(login);
const fetchMeMock = vi.mocked(fetchMe);
const googleStatusMock = vi.mocked(fetchGoogleOAuthStatus);

function renderLogin(route = '/login') {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<div>page-inscription</div>} />
      <Route path="/me" element={<div>page-espace</div>} />
      <Route path="/password" element={<div>page-mot-de-passe</div>} />
    </Routes>,
    route,
  );
}

async function submitCredentials(email = 'camille@exemple.com', password = 'motdepasse1') {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText(fr.auth.login.emailPlaceholder), email);
  await user.type(screen.getByPlaceholderText(fr.auth.login.passwordPlaceholder), password);
  await user.click(screen.getByRole('button', { name: fr.auth.login.submit }));
}

beforeEach(() => {
  setAuthUser(null);
  loginMock.mockReset();
  fetchMeMock.mockReset();
  googleStatusMock.mockReset();
  googleStatusMock.mockResolvedValue({ enabled: false });
});

describe('LoginPage', () => {
  it('renders the French form and the single SANAA brand', () => {
    renderLogin();

    expect(screen.getByRole('heading', { name: fr.auth.login.title, level: 1 })).toBeVisible();
    expect(screen.getByText(fr.auth.login.emailLabel)).toBeVisible();
    expect(screen.getByText(fr.auth.login.passwordLabel)).toBeVisible();
    expect(
      screen.getByRole('button', { name: fr.auth.login.submit }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: 'SANAA' }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(fr.app.tagline)).toBeVisible();
  });

  it('links to registration and to creator onboarding without a tenant parameter', () => {
    renderLogin('/login');

    expect(screen.getByRole('link', { name: fr.auth.login.createAccount })).toHaveAttribute(
      'href',
      '/register',
    );
    expect(screen.getByRole('link', { name: fr.auth.login.becomeCreator })).toHaveAttribute(
      'href',
      '/become-creator',
    );
  });

  it('keeps password login and adds Google when the backend enables it', async () => {
    googleStatusMock.mockResolvedValue({ enabled: true });
    renderLogin();

    expect(screen.getByRole('button', { name: fr.auth.login.submit })).toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: fr.auth.google.continue }),
    ).toBeInTheDocument();
  });

  it('shows the French credentials message on a 401 and sends only email and password', async () => {
    loginMock.mockRejectedValue(
      new ApiError(401, { title: 'Unauthorized', detail: 'Invalid email or password.' }),
    );
    renderLogin();

    await submitCredentials();

    expect(
      await screen.findByText(fr.errors.invalidCredentials),
    ).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith({
      email: 'camille@exemple.com',
      password: 'motdepasse1',
    });
  });

  it('opens the personal space after a successful login', async () => {
    loginMock.mockResolvedValue({ accessToken: 'access-token', expiresIn: 900 });
    fetchMeMock.mockResolvedValue(studentUser);
    renderLogin();

    await submitCredentials();

    expect(await screen.findByText('page-espace')).toBeInTheDocument();
    expect(useAuthStore.getState().accessToken).toBe('access-token');
  });

  it('sends a temporary-password session straight to the password page', async () => {
    loginMock.mockResolvedValue({ accessToken: 'access-token', expiresIn: 900 });
    fetchMeMock.mockResolvedValue({ ...studentUser, mustChangePassword: true });
    renderLogin();

    await submitCredentials();

    expect(await screen.findByText('page-mot-de-passe')).toBeInTheDocument();
  });

  it('does not show the form to a session that is already signed in', () => {
    setAuthUser(studentUser);
    renderLogin();

    expect(screen.getByText('page-espace')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: fr.auth.login.submit }),
    ).not.toBeInTheDocument();
  });
});
