import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { fr } from '../i18n/fr';
import { rememberGoogleRedirect } from '../lib/googleAuth';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { GoogleCallbackPage } from './GoogleCallbackPage';

beforeEach(() => {
  setAuthUser(null);
  window.sessionStorage.clear();
});

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/login/google/callback" element={<GoogleCallbackPage />} />
      <Route path="/courses/7" element={<div>page-cours</div>} />
    </Routes>,
    '/login/google/callback',
  );
}

describe('GoogleCallbackPage', () => {
  it('continues to the remembered internal route after session restore', () => {
    rememberGoogleRedirect('/courses/7');
    setAuthUser(studentUser);
    renderPage();
    expect(screen.getByText('page-cours')).toBeInTheDocument();
  });

  it('shows a French recovery state when no session was created', () => {
    renderPage();
    expect(
      screen.getByRole('heading', { level: 1, name: fr.auth.google.callbackErrorTitle }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: fr.auth.google.backToLogin })).toHaveAttribute(
      'href',
      '/login',
    );
  });
});
