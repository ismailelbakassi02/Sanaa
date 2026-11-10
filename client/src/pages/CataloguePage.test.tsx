import { cleanup, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PageResponse, CourseSummary } from '../api/courses';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, studentUser } from '../test/utils';
import { CataloguePage } from './CataloguePage';
import { useCatalogue } from '../hooks/courses';

vi.mock('../hooks/courses', () => ({
  CATALOGUE_PAGE_SIZE: 12,
  useCatalogue: vi.fn(),
}));

function course(id: number, title: string): CourseSummary {
  return {
    id,
    title,
    description: null,
    price: 0,
    currency: 'MAD',
    status: 'PUBLISHED',
    instructorId: 100,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  };
}

function page(items: CourseSummary[]): PageResponse<CourseSummary> {
  return { items, page: 0, size: 12, totalElements: items.length, totalPages: 1 };
}

function result(overrides: Record<string, unknown>) {
  return {
    isPending: false,
    isError: false,
    data: page([]),
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCatalogue>;
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'token',
    user: studentUser,
    status: 'authenticated',
    sessionStartedAt: null,
  });
});

describe('CataloguePage', () => {
  it('shows a skeleton, then the cards once the query resolves', async () => {
    vi.mocked(useCatalogue).mockReturnValue(result({ isPending: true, data: undefined }));
    renderWithProviders(<CataloguePage />, '/courses');

    expect(screen.getByTestId('catalogue-skeleton')).toBeInTheDocument();

    cleanup();
    vi.mocked(useCatalogue).mockReturnValue(
      result({ data: page([course(7, 'Montage vidéo : les bases')]) }),
    );
    renderWithProviders(<CataloguePage />, '/courses');

    expect(screen.queryByTestId('catalogue-skeleton')).not.toBeInTheDocument();
    expect(await screen.findByText('Montage vidéo : les bases')).toBeInTheDocument();
    expect(screen.getByText('Gratuit')).toBeInTheDocument();
  });

  it('shows the empty state when no course is available', async () => {
    vi.mocked(useCatalogue).mockReturnValue(result({ data: page([]) }));
    renderWithProviders(<CataloguePage />, '/courses');

    expect(await screen.findByText(fr.catalogue.empty)).toBeInTheDocument();
  });

  it('shows a French error with a retry button', async () => {
    const refetch = vi.fn();
    vi.mocked(useCatalogue).mockReturnValue(result({ isError: true, refetch }));
    renderWithProviders(<CataloguePage />, '/courses');

    expect(await screen.findByText(fr.catalogue.loadErrorTitle)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: fr.common.retry })).toBeInTheDocument();
  });

  it('hides the pager when every result fits on one page', async () => {
    vi.mocked(useCatalogue).mockReturnValue(result({ data: page([course(3, 'Cours unique')]) }));
    renderWithProviders(<CataloguePage />, '/courses');

    await screen.findByText('Cours unique');
    expect(screen.queryByTestId('catalogue-pagination')).not.toBeInTheDocument();
  });

  it('shows the pager when the catalogue has more than one page', async () => {
    const items = Array.from({ length: 12 }, (_, index) => course(index + 1, `Cours ${index + 1}`));
    vi.mocked(useCatalogue).mockReturnValue(
      result({ data: { items, page: 0, size: 12, totalElements: 13, totalPages: 2 } }),
    );
    renderWithProviders(<CataloguePage />, '/courses');

    await screen.findByText('Cours 1');
    expect(screen.getByTestId('catalogue-pagination')).toBeInTheDocument();
  });
});
