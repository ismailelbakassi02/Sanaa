import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { RequireRole } from './RequireRole';
import type { Role } from '../api/types';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, studentUser } from '../test/utils';

function renderGuard(roles?: readonly Role[]) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<div>page-connexion</div>} />
      <Route
        path="/"
        element={
          <RequireRole roles={roles}>
            <div>contenu-protege</div>
          </RequireRole>
        }
      />
    </Routes>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
});

describe('RequireRole', () => {
  it('redirects an anonymous visitor to the login page', () => {
    renderGuard();
    expect(screen.getByText('page-connexion')).toBeInTheDocument();
  });

  it('renders the protected content for an allowed role', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: studentUser,
      status: 'authenticated',
    });
    renderGuard(['STUDENT']);
    expect(screen.getByText('contenu-protege')).toBeInTheDocument();
  });

  it('renders the French forbidden page for a disallowed role', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: studentUser,
      status: 'authenticated',
    });
    renderGuard(['TENANT_ADMIN']);
    expect(screen.getByText('Accès refusé')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: "Retour à l'accueil" })).toBeInTheDocument();
  });

  it('accepts any authenticated user when no role is required', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: studentUser,
      status: 'authenticated',
    });
    renderGuard();
    expect(screen.getByText('contenu-protege')).toBeInTheDocument();
  });
});
