import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { confirmGoogleLink, fetchMe } from '../api/auth';
import { fr } from '../i18n/fr';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { GoogleLinkPage } from './GoogleLinkPage';

vi.mock('../api/auth', () => ({
  confirmGoogleLink: vi.fn(),
  fetchMe: vi.fn(),
}));

const confirmMock = vi.mocked(confirmGoogleLink);
const fetchMeMock = vi.mocked(fetchMe);

beforeEach(() => {
  setAuthUser(null);
  confirmMock.mockReset();
  fetchMeMock.mockReset();
  window.sessionStorage.clear();
});

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/login/google/link" element={<GoogleLinkPage />} />
      <Route path="/me" element={<div>page-espace</div>} />
    </Routes>,
    '/login/google/link',
  );
}

describe('GoogleLinkPage', () => {
  it('confirms the existing password and opens the account', async () => {
    confirmMock.mockResolvedValue({ accessToken: 'google-access', expiresIn: 900 });
    fetchMeMock.mockResolvedValue(studentUser);
    renderPage();

    await userEvent.type(
      screen.getByPlaceholderText(fr.auth.google.linkPasswordPlaceholder),
      'mot de passe existant',
    );
    await userEvent.click(screen.getByRole('button', { name: fr.auth.google.linkSubmit }));

    expect(confirmMock).toHaveBeenCalledWith({ password: 'mot de passe existant' });
    expect(await screen.findByText('page-espace')).toBeInTheDocument();
  });

  it('redirects an already authenticated session to the account', () => {
    setAuthUser(studentUser);
    renderPage();
    expect(screen.getByText('page-espace')).toBeInTheDocument();
  });
});
