import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fr } from './i18n/fr';
import { renderWithProviders, setAuthUser } from './test/utils';
import { App } from './App';

vi.mock('./hooks/catalogue', () => ({
  useCategories: () => ({ data: [], isPending: false, isError: false }),
  useTrending: () => ({ data: [], isPending: false, isError: false }),
}));

vi.mock('./hooks/learner', () => ({
  useLearnerHome: () => ({ data: undefined, isPending: false, isError: true }),
}));

beforeEach(() => {
  setAuthUser(null);
});

describe('App routes', () => {
  it('renders a lazily loaded public page behind the Suspense fallback', async () => {
    renderWithProviders(<App />, '/');

    expect(
      await screen.findByRole('heading', { level: 1, name: fr.home.heroTitle }),
    ).toBeInTheDocument();
  });

  it('sends an anonymous visitor of the learner area to the login page', async () => {
    renderWithProviders(<App />, '/learn');

    expect(
      await screen.findByRole('heading', { name: fr.auth.login.title }),
    ).toBeInTheDocument();
    expect(screen.queryByText(fr.learner.home.title)).not.toBeInTheDocument();
  });
});
