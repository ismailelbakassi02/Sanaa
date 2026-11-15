import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import type { MeResponse, Role } from '../api/types';
import { useAuthStore } from '../store/auth';
import {
  individualAdminUser,
  instructorUser,
  organizationAdminUser,
  renderWithProviders,
  setAuthUser,
  studentUser,
  superAdminUser,
} from '../test/utils';
import { ForcedPasswordGate } from './ForcedPasswordGate';
import { RequireRole } from './RequireRole';

const STUDIO_ROLES = ['TENANT_ADMIN', 'INSTRUCTOR'] as const satisfies readonly Role[];

function GuardHarness() {
  return (
    <Routes>
      <Route path="/login" element={<div>page-connexion</div>} />
      <Route path="/password" element={<div>page-mot-de-passe</div>} />
      <Route path="/me" element={<div>page-espace</div>} />
      <Route path="/" element={<div>page-accueil</div>} />
      <Route
        path="/studio/profile"
        element={
          <RequireRole roles={STUDIO_ROLES}>
            <div>contenu-studio</div>
          </RequireRole>
        }
      />
      <Route
        path="/studio/team"
        element={
          <RequireRole roles={['TENANT_ADMIN']}>
            <div>contenu-equipe</div>
          </RequireRole>
        }
      />
      <Route
        path="/admin"
        element={
          <RequireRole roles={['SUPER_ADMIN']}>
            <div>contenu-admin</div>
          </RequireRole>
        }
      />
      <Route
        path="/gate/*"
        element={
          <ForcedPasswordGate>
            <div>contenu-portail</div>
          </ForcedPasswordGate>
        }
      />
    </Routes>
  );
}

function renderGuard(route: string, user: MeResponse | null) {
  setAuthUser(user);
  return renderWithProviders(<GuardHarness />, route);
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
  it('sends an anonymous visitor to the login page', () => {
    renderGuard('/studio/profile', null);
    expect(screen.getByText('page-connexion')).toBeInTheDocument();
  });

  it('sends a learner away from a studio route to their space', () => {
    renderGuard('/studio/profile', studentUser);
    expect(screen.getByText('page-espace')).toBeInTheDocument();
    expect(screen.queryByText('contenu-studio')).not.toBeInTheDocument();
  });

  it('lets an instructor open the studio profile', () => {
    renderGuard('/studio/profile', instructorUser);
    expect(screen.getByText('contenu-studio')).toBeInTheDocument();
  });

  it('keeps an instructor out of the team route', () => {
    renderGuard('/studio/team', instructorUser);
    expect(screen.getByText('page-espace')).toBeInTheDocument();
    expect(screen.queryByText('contenu-equipe')).not.toBeInTheDocument();
  });

  it('lets an individual administrator reach the team page, which explains the restriction', () => {
    renderGuard('/studio/team', individualAdminUser);
    expect(screen.getByText('contenu-equipe')).toBeInTheDocument();
  });

  it('lets an organization administrator open the team route', () => {
    renderGuard('/studio/team', organizationAdminUser);
    expect(screen.getByText('contenu-equipe')).toBeInTheDocument();
  });

  it('reserves the platform administration to the super administrator', () => {
    renderGuard('/admin', superAdminUser);
    expect(screen.getByText('contenu-admin')).toBeInTheDocument();
  });

  it.each([
    ['learner', studentUser],
    ['instructor', instructorUser],
    ['administrator', organizationAdminUser],
  ] as const)('redirects a %s away from /admin', (_label, user) => {
    renderGuard('/admin', user);
    expect(screen.getByText('page-espace')).toBeInTheDocument();
    expect(screen.queryByText('contenu-admin')).not.toBeInTheDocument();
  });

  it('redirects a super administrator away from the studio profile', () => {
    renderGuard('/studio/profile', superAdminUser);
    expect(screen.getByText('page-espace')).toBeInTheDocument();
    expect(screen.queryByText('contenu-studio')).not.toBeInTheDocument();
  });

  it('forces a temporary-password session to the password page', () => {
    renderGuard('/studio/profile', { ...instructorUser, mustChangePassword: true });
    expect(screen.getByText('page-mot-de-passe')).toBeInTheDocument();
    expect(screen.queryByText('contenu-studio')).not.toBeInTheDocument();
  });
});

describe('ForcedPasswordGate', () => {
  it('redirects any other route to /password', () => {
    renderGuard('/gate/anything', { ...studentUser, mustChangePassword: true });
    expect(screen.getByText('page-mot-de-passe')).toBeInTheDocument();
    expect(screen.queryByText('contenu-portail')).not.toBeInTheDocument();
  });

  it('lets a normal session through', () => {
    renderGuard('/gate/anything', studentUser);
    expect(screen.getByText('contenu-portail')).toBeInTheDocument();
  });
});
