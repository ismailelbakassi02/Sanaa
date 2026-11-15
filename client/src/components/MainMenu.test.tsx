import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

import type { Role } from '../api/types';
import { fr } from '../i18n/fr';
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
import { MainMenu, navItemsForRole, navItemsForUser } from './MainMenu';

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
});

describe('navItemsForRole', () => {
  it('gives a learner only the public, personal and creator-onboarding entries', () => {
    expect(navItemsForRole('STUDENT').map((item) => item.path)).toEqual([
      '/',
      '/me',
      '/become-creator',
    ]);
  });

  it('gives an instructor the read-only studio profile but no team', () => {
    expect(navItemsForRole('INSTRUCTOR', 'ORGANIZATION').map((item) => item.path)).toEqual([
      '/',
      '/me',
      '/studio/profile',
    ]);
  });

  it('adds the team entry only to an organization administrator', () => {
    expect(navItemsForRole('TENANT_ADMIN', 'INDIVIDUAL').map((item) => item.path)).toEqual([
      '/',
      '/me',
      '/studio/profile',
    ]);
    expect(navItemsForRole('TENANT_ADMIN', 'ORGANIZATION').map((item) => item.path)).toEqual([
      '/',
      '/me',
      '/studio/profile',
      '/studio/team',
    ]);
    // An organization administrator without the type hint keeps the safe subset.
    expect(navItemsForRole('TENANT_ADMIN').map((item) => item.path)).not.toContain(
      '/studio/team',
    );
  });

  it('gives the platform administrator only the platform entries', () => {
    expect(navItemsForRole('SUPER_ADMIN').map((item) => item.path)).toEqual([
      '/',
      '/me',
      '/admin',
    ]);
  });

  it('never exposes a course, catalogue or payment route in any menu', () => {
    const roles: Role[] = ['STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN', 'SUPER_ADMIN'];
    const hidden = ['/courses', '/manage', '/my-courses', '/my-payments', '/payments'];
    for (const role of roles) {
      for (const path of navItemsForRole(role, 'ORGANIZATION').map((item) => item.path)) {
        expect(hidden).not.toContain(path);
      }
    }
  });

  it('returns an empty menu for an anonymous visitor', () => {
    expect(navItemsForUser(null)).toEqual([]);
  });
});

describe('MainMenu rendering', () => {
  it('renders the learner entries', () => {
    setAuthUser(studentUser);
    renderWithProviders(<MainMenu />, '/me');

    expect(screen.getByText(fr.nav.home)).toBeInTheDocument();
    expect(screen.getByText(fr.nav.me)).toBeInTheDocument();
    expect(screen.getByText(fr.nav.becomeCreator)).toBeInTheDocument();
    expect(screen.queryByText(fr.nav.studioProfile)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.nav.admin)).not.toBeInTheDocument();
  });

  it('renders the studio profile for an instructor', () => {
    setAuthUser(instructorUser);
    renderWithProviders(<MainMenu />, '/studio/profile');

    expect(screen.getByText(fr.nav.studioProfile)).toBeInTheDocument();
    expect(screen.queryByText(fr.nav.studioTeam)).not.toBeInTheDocument();
  });

  it('renders the team entry for an organization administrator', () => {
    setAuthUser(organizationAdminUser);
    renderWithProviders(<MainMenu />, '/studio/team');

    expect(screen.getByText(fr.nav.studioProfile)).toBeInTheDocument();
    expect(screen.getByText(fr.nav.studioTeam)).toBeInTheDocument();
  });

  it('marks the entry of the current route as selected', () => {
    setAuthUser(organizationAdminUser);
    const { container } = renderWithProviders(<MainMenu />, '/studio/team');

    // Arco matches selectedKeys against the item key, which is the route path.
    const selected = container.querySelector('.arco-menu-selected');
    expect(selected).not.toBeNull();
    expect(selected).toHaveTextContent(fr.nav.studioTeam);
  });

  it('hides the team entry for an individual administrator', () => {
    setAuthUser(individualAdminUser);
    renderWithProviders(<MainMenu />, '/studio/profile');

    expect(screen.queryByText(fr.nav.studioTeam)).not.toBeInTheDocument();
  });

  it('renders the platform administration entry for a super administrator', () => {
    setAuthUser(superAdminUser);
    renderWithProviders(<MainMenu />, '/admin');

    expect(screen.getByText(fr.nav.admin)).toBeInTheDocument();
    expect(screen.queryByText(fr.nav.studioProfile)).not.toBeInTheDocument();
  });

  it('renders nothing for an anonymous visitor', () => {
    const { container } = renderWithProviders(<MainMenu />);
    expect(container).toBeEmptyDOMElement();
  });
});
