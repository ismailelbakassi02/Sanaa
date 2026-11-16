import { act, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CourseSearchParams, PublicCourseCard } from '../api/catalogue';
import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';
import { CoursesPage } from './CoursesPage';

const state = vi.hoisted(() => ({
  searches: [] as unknown[],
  refetch: vi.fn(),
}));

const course: PublicCourseCard = {
  id: 1,
  title: 'Étalonnage et colorimétrie',
  summary: null,
  level: 'BEGINNER',
  language: 'fr',
  price: 0,
  effectivePrice: 0,
  currency: 'MAD',
  discountActive: false,
  ratingAvg: 0,
  ratingCount: 0,
  studentCount: 12,
  instructorName: 'Nadia Créatrice',
  publisherName: 'Atelier Lumière',
  publisherSlug: 'atelier-lumiere',
  publisherType: 'ORGANIZATION',
  categorySlug: 'image',
  categoryName: 'Image',
  tags: [],
};

vi.mock('../hooks/catalogue', () => ({
  useCategories: () => ({
    data: [{ id: 1, slug: 'image', name: 'Image', courseCount: 1 }],
    isPending: false,
    isError: false,
  }),
  useCourseSearch: (params: CourseSearchParams) => {
    state.searches.push(params);
    return {
      data: { items: [course], page: 0, size: 24, totalElements: 1, totalPages: 1 },
      isPending: false,
      isError: false,
      refetch: state.refetch,
    };
  },
}));

function lastSearch(): CourseSearchParams {
  return state.searches[state.searches.length - 1] as CourseSearchParams;
}

beforeEach(() => {
  state.searches = [];
  state.refetch.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('CoursesPage', () => {
  it('renders the catalogue result and its count', () => {
    renderWithProviders(<CoursesPage />, '/courses');

    expect(screen.getByText('Étalonnage et colorimétrie')).toBeInTheDocument();
    expect(screen.getByText(fr.search.resultCount(1))).toBeInTheDocument();
  });

  it('waits for the 300 ms debounce before searching', () => {
    vi.useFakeTimers();
    renderWithProviders(<CoursesPage />, '/courses');

    fireEvent.change(screen.getByLabelText(fr.search.searchLabel), {
      target: { value: 'etalonnage' },
    });
    expect(lastSearch().q).toBeUndefined();

    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(lastSearch().q).toBeUndefined();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(lastSearch().q).toBe('etalonnage');
  });

  it('removes a filter chip and re-queries without it', () => {
    renderWithProviders(<CoursesPage />, '/courses?q=photo&level=BEGINNER');

    const removeQuery = screen.getByRole('img', {
      name: fr.search.removeChip(fr.search.chipQuery('photo')),
    });
    fireEvent.click(removeQuery);

    expect(lastSearch().q).toBeUndefined();
    expect(lastSearch().level).toBe('BEGINNER');
    expect(
      screen.queryByRole('img', {
        name: fr.search.removeChip(fr.search.chipQuery('photo')),
      }),
    ).not.toBeInTheDocument();
  });

  it('resets every filter from the empty state', () => {
    renderWithProviders(<CoursesPage />, '/courses?q=inexistant');

    fireEvent.click(screen.getByRole('button', { name: fr.search.reset }));

    expect(lastSearch().q).toBeUndefined();
    expect(lastSearch().page).toBe(0);
  });
});
