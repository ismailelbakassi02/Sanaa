import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';
import { ReviewForm, REVIEW_COMMENT_MAX } from './ReviewForm';

const state = vi.hoisted(() => ({
  mutate: vi.fn(),
  review: null as
    | {
        id: number;
        courseId: number;
        rating: number;
        comment: string | null;
        status: 'VISIBLE' | 'HIDDEN';
        createdAt: string;
        updatedAt: string;
        publisherReply: string | null;
        repliedAt: string | null;
      }
    | null,
}));

vi.mock('../hooks/learner', () => ({
  useMyReview: () => ({ data: state.review, isError: false }),
  useSaveReview: () => ({ mutate: state.mutate, isPending: false }),
}));

beforeEach(() => {
  state.review = null;
  state.mutate.mockReset();
});

describe('ReviewForm', () => {
  it('renders nothing when the learner is not enrolled', () => {
    const { container } = renderWithProviders(<ReviewForm courseId={7} canReview={false} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('requires a rating before calling the API', async () => {
    renderWithProviders(<ReviewForm courseId={7} canReview />);

    await userEvent.click(
      screen.getByRole('button', { name: fr.coursePage.review.submit }),
    );

    expect(screen.getByText(fr.coursePage.review.ratingRequired)).toBeInTheDocument();
    expect(state.mutate).not.toHaveBeenCalled();
  });

  it('rejects a comment longer than the limit before calling the API', async () => {
    state.review = {
      id: 1,
      courseId: 7,
      rating: 4,
      comment: 'a'.repeat(REVIEW_COMMENT_MAX + 1),
      status: 'VISIBLE',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      publisherReply: null,
      repliedAt: null,
    };
    renderWithProviders(<ReviewForm courseId={7} canReview />);

    await userEvent.click(
      screen.getByRole('button', { name: fr.coursePage.review.update }),
    );

    expect(screen.getByText(fr.coursePage.review.tooLong)).toBeInTheDocument();
    expect(state.mutate).not.toHaveBeenCalled();
  });

  it('tells the author that a hidden review is not public and keeps it editable', () => {
    state.review = {
      id: 3,
      courseId: 7,
      rating: 2,
      comment: 'Moyen',
      status: 'HIDDEN',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-02T10:00:00Z',
      publisherReply: 'Merci pour votre retour.',
      repliedAt: '2026-01-03T10:00:00Z',
    };
    renderWithProviders(<ReviewForm courseId={7} canReview />);

    expect(screen.getByText(fr.coursePage.review.hidden)).toBeInTheDocument();
    expect(screen.getByText('Merci pour votre retour.')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: fr.coursePage.review.update }),
    ).toBeInTheDocument();
  });

  it('sends an update when a review already exists', async () => {
    state.review = {
      id: 4,
      courseId: 7,
      rating: 5,
      comment: 'Très bien',
      status: 'VISIBLE',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      publisherReply: null,
      repliedAt: null,
    };
    renderWithProviders(<ReviewForm courseId={7} canReview />);

    await userEvent.click(
      screen.getByRole('button', { name: fr.coursePage.review.update }),
    );

    expect(state.mutate).toHaveBeenCalledWith(
      { rating: 5, comment: 'Très bien' },
      expect.anything(),
    );
  });

  it('shows the character counter', () => {
    renderWithProviders(<ReviewForm courseId={7} canReview />);
    expect(screen.getByText(fr.coursePage.review.counter(0))).toBeInTheDocument();
  });
});
