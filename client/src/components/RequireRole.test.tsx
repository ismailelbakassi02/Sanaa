import { screen } from '@testing-library/react';
import { Route, Routes, useLocation } from 'react-router-dom';
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

function HomeProbe() {
  const location = useLocation();
  return <div>{`accueil${location.search}`}</div>;
}

function renderPaymentGuard(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<HomeProbe />} />
      <Route
        path="/my-payments"
        element={
          <RequireRole roles={['STUDENT']} redirectOnForbidden>
            <div>mes-paiements</div>
          </RequireRole>
        }
      />
      <Route
        path="/payments"
        element={
          <RequireRole roles={['TENANT_ADMIN']} redirectOnForbidden>
            <div>paiements-admin</div>
          </RequireRole>
        }
      />
    </Routes>,
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

  it('shows the student payments route to a student', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: studentUser,
      status: 'authenticated',
    });
    renderPaymentGuard('/my-payments');
    expect(screen.getByText('mes-paiements')).toBeInTheDocument();
  });

  it('shows the admin payments route to a tenant administrator', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: { ...studentUser, role: 'TENANT_ADMIN' },
      status: 'authenticated',
    });
    renderPaymentGuard('/payments');
    expect(screen.getByText('paiements-admin')).toBeInTheDocument();
  });

  it.each(['STUDENT', 'INSTRUCTOR', 'SUPER_ADMIN'] as const)(
    'redirects %s away from the admin payments route and keeps the tenant',
    (role) => {
      useAuthStore.setState({
        accessToken: 'token',
        user: { ...studentUser, role },
        status: 'authenticated',
      });
      renderPaymentGuard('/payments?tenant=atlas');
      expect(screen.getByText('accueil?tenant=atlas')).toBeInTheDocument();
      expect(screen.queryByText('paiements-admin')).not.toBeInTheDocument();
    },
  );

  it('redirects a tenant administrator away from the student payments route', () => {
    useAuthStore.setState({
      accessToken: 'token',
      user: { ...studentUser, role: 'TENANT_ADMIN' },
      status: 'authenticated',
    });
    renderPaymentGuard('/my-payments?tenant=atlas');
    expect(screen.getByText('accueil?tenant=atlas')).toBeInTheDocument();
    expect(screen.queryByText('mes-paiements')).not.toBeInTheDocument();
  });
});
