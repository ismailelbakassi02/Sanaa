import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchGoogleOAuthStatus, fetchMe, login, register } from '../api/auth';
import { ApiError } from '../api/errors';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { RegisterPage } from './RegisterPage';

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  restoreSession: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  fetchGoogleOAuthStatus: vi.fn(),
}));

const registerMock = vi.mocked(register);
const loginMock = vi.mocked(login);
const fetchMeMock = vi.mocked(fetchMe);
const googleStatusMock = vi.mocked(fetchGoogleOAuthStatus);

function renderRegister() {
  return renderWithProviders(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<div>page-connexion</div>} />
      <Route path="/me" element={<div>page-espace</div>} />
    </Routes>,
    '/register',
  );
}

async function fillForm(password: string, confirmation = password) {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText(fr.auth.register.namePlaceholder), 'Camille Martin');
  await user.type(
    screen.getByPlaceholderText(fr.auth.register.emailPlaceholder),
    'camille@exemple.com',
  );
  await user.type(screen.getByPlaceholderText(fr.auth.register.passwordPlaceholder), password);
  await user.type(
    screen.getByPlaceholderText(fr.auth.register.confirmPlaceholder),
    confirmation,
  );
  await user.click(screen.getByRole('button', { name: fr.auth.register.submit }));
}

beforeEach(() => {
  setAuthUser(null);
  registerMock.mockReset();
  loginMock.mockReset();
  fetchMeMock.mockReset();
  googleStatusMock.mockReset();
  googleStatusMock.mockResolvedValue({ enabled: false });
});

describe('RegisterPage', () => {
  it('renders the French learner form and a plain login link', () => {
    renderRegister();

    expect(screen.getByRole('heading', { name: fr.auth.register.title, level: 1 })).toBeVisible();
    expect(screen.getByRole('link', { name: fr.auth.register.signIn })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(screen.getByText(fr.auth.register.subtitle)).toBeVisible();
  });

  it('keeps normal registration and offers Google when configured', async () => {
    googleStatusMock.mockResolvedValue({ enabled: true });
    renderRegister();

    expect(screen.getByRole('button', { name: fr.auth.register.submit })).toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: fr.auth.google.continue }),
    ).toBeInTheDocument();
  });

  it('refuses a password shorter than 10 bytes before calling the API', async () => {
    renderRegister();
    await fillForm('court');

    expect(await screen.findByText(fr.common.passwordMin)).toBeInTheDocument();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('refuses two different passwords before calling the API', async () => {
    renderRegister();
    await fillForm('motdepasse1', 'motdepasse2');

    expect(await screen.findByText(fr.auth.register.passwordMismatch)).toBeInTheDocument();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('shows the French conflict message when the e-mail is already registered', async () => {
    registerMock.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        detail: 'Email is already registered.',
      }),
    );
    renderRegister();
    await fillForm('motdepasse1');

    expect(await screen.findByText(fr.errors.emailAlreadyRegistered)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: fr.auth.register.submit })).toBeInTheDocument();
  });

  it('creates the account then signs the learner in', async () => {
    registerMock.mockResolvedValue({
      id: 1,
      email: 'camille@exemple.com',
      fullName: 'Camille Martin',
      role: 'STUDENT',
    });
    loginMock.mockResolvedValue({ accessToken: 'new-token', expiresIn: 900 });
    fetchMeMock.mockResolvedValue(studentUser);
    renderRegister();

    await fillForm('motdepasse1');

    expect(await screen.findByText('page-espace')).toBeInTheDocument();
    expect(registerMock).toHaveBeenCalledWith({
      email: 'camille@exemple.com',
      password: 'motdepasse1',
      fullName: 'Camille Martin',
    });
    expect(loginMock).toHaveBeenCalledWith({
      email: 'camille@exemple.com',
      password: 'motdepasse1',
    });
    expect(useAuthStore.getState().status).toBe('authenticated');
  });
});
