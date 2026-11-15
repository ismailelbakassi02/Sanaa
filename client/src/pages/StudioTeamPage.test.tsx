import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

import { fr } from '../i18n/fr';
import { individualAdminUser, renderWithProviders, setAuthUser } from '../test/utils';
import { StudioTeamPage } from './StudioTeamPage';

beforeEach(() => {
  setAuthUser(individualAdminUser);
});

describe('StudioTeamPage', () => {
  it('explains in French that an individual publisher has no team', () => {
    renderWithProviders(<StudioTeamPage />, '/studio/team');

    expect(screen.getByText(fr.studio.team.organizationOnlyTitle)).toBeVisible();
    expect(screen.getByText(fr.studio.team.organizationOnlyBody)).toBeVisible();
    expect(
      screen.getByRole('link', { name: fr.me.studio }),
    ).toHaveAttribute('href', '/studio/profile');
    // The add-member action must not be offered.
    expect(
      screen.queryByRole('button', { name: fr.studio.team.add }),
    ).not.toBeInTheDocument();
  });
});
