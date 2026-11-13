import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

import { MainMenu, navItemsForRole } from './MainMenu';
import type { MeResponse, Role } from '../api/types';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, studentUser } from '../test/utils';

function userWithRole(role: Role): MeResponse {
  return { ...studentUser, role };
}

function setUser(role: Role) {
  useAuthStore.setState({
    accessToken: 'token',
    user: userWithRole(role),
    status: 'authenticated',
    sessionStartedAt: null,
  });
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
});

describe('MainMenu', () => {
  it('shows catalogue, enrollments and payments for a student', () => {
    setUser('STUDENT');
    renderWithProviders(<MainMenu />, '/courses');
    expect(screen.getByText(fr.menu.catalogue)).toBeInTheDocument();
    expect(screen.getByText(fr.menu.myCourses)).toBeInTheDocument();
    expect(screen.getByText(fr.menu.myPayments)).toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myFormations)).not.toBeInTheDocument();
  });

  it('shows the authoring entry for an instructor without payments', () => {
    setUser('INSTRUCTOR');
    renderWithProviders(<MainMenu />, '/manage');
    expect(screen.getByText(fr.menu.catalogue)).toBeInTheDocument();
    expect(screen.getByText(fr.menu.myFormations)).toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myCourses)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.payments)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myPayments)).not.toBeInTheDocument();
  });

  it('shows formations and payments for a tenant administrator', () => {
    setUser('TENANT_ADMIN');
    renderWithProviders(<MainMenu />, '/manage');
    expect(screen.getByText(fr.menu.catalogue)).toBeInTheDocument();
    expect(screen.getByText(fr.menu.myFormations)).toBeInTheDocument();
    expect(screen.getByText(fr.menu.payments)).toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myCourses)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myPayments)).not.toBeInTheDocument();
  });

  it('shows no course entry for a super administrator', () => {
    setUser('SUPER_ADMIN');
    renderWithProviders(<MainMenu />, '/');
    expect(screen.queryByText(fr.menu.catalogue)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myCourses)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myFormations)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.payments)).not.toBeInTheDocument();
    expect(screen.queryByText(fr.menu.myPayments)).not.toBeInTheDocument();
  });

  it('keeps the payment route out of the instructor and super administrator menus', () => {
    expect(navItemsForRole('INSTRUCTOR').map((item) => item.path)).toEqual([
      '/courses',
      '/manage',
    ]);
    expect(navItemsForRole('SUPER_ADMIN')).toEqual([]);
  });
});
