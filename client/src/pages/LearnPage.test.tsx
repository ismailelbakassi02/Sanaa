import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fr } from '../i18n/fr';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { LearnPage } from './LearnPage';

vi.mock('../hooks/learner', () => ({
  useLearnerHome: () => ({
    data: {
      myCourses: [],
      myTeachers: [],
      suggestedCategories: [],
      trending: [],
      categories: [],
    },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

vi.mock('../hooks/catalogue', () => ({
  useTrending: () => ({ data: [], isPending: false }),
  useCategories: () => ({ data: [] }),
}));

beforeEach(() => {
  setAuthUser(studentUser);
});

describe('LearnPage', () => {
  it('uses a single h1 and greets the learner', () => {
    renderWithProviders(<LearnPage />, '/learn');

    const headings = screen.getAllByRole('heading', { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent(fr.learner.home.title);
    expect(screen.getByText(fr.learner.home.greeting(studentUser.fullName))).toBeInTheDocument();
  });

  it('invites the learner to the catalogue when there is no enrollment', () => {
    renderWithProviders(<LearnPage />, '/learn');

    expect(screen.getByText(fr.learner.home.emptyTitle)).toBeInTheDocument();
    expect(screen.getByText(fr.learner.home.emptyBody)).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: fr.learner.home.emptyAction }),
    ).toBeInTheDocument();
  });
});
