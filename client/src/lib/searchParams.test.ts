import { describe, expect, it } from 'vitest';

import {
  DEFAULT_FILTERS,
  hasActiveFilters,
  parseCatalogueSearch,
  serializeCatalogueSearch,
} from './searchParams';

describe('parseCatalogueSearch', () => {
  it('returns the defaults for an empty query string', () => {
    expect(parseCatalogueSearch('')).toEqual(DEFAULT_FILTERS);
  });

  it('reads every supported filter', () => {
    expect(
      parseCatalogueSearch(
        '?q=photo&category=image&tag=studio&level=ADVANCED&free=true&sort=rating&page=3',
      ),
    ).toEqual({
      q: 'photo',
      category: 'image',
      tag: 'studio',
      level: 'ADVANCED',
      free: true,
      sort: 'rating',
      page: 3,
    });
  });

  it('drops values the server would reject', () => {
    expect(
      parseCatalogueSearch('?level=EXPERT&sort=random&page=-2&free=yes'),
    ).toEqual(DEFAULT_FILTERS);
  });

  it('trims text filters', () => {
    expect(parseCatalogueSearch('?q=%20photo%20').q).toBe('photo');
  });
});

describe('serializeCatalogueSearch', () => {
  it('omits the defaults so the address stays short', () => {
    expect(serializeCatalogueSearch(DEFAULT_FILTERS)).toBe('');
  });

  it('keeps a stable order and encodes the values', () => {
    expect(
      serializeCatalogueSearch({
        q: 'étalonnage',
        category: 'image',
        tag: 'studio',
        level: 'BEGINNER',
        free: true,
        sort: 'rating',
        page: 2,
      }),
    ).toBe(
      'q=%C3%A9talonnage&category=image&tag=studio&level=BEGINNER&free=true&sort=rating&page=2',
    );
  });

  it('round-trips every filter', () => {
    const filters = {
      q: 'montage',
      category: 'video',
      tag: '',
      level: 'INTERMEDIATE' as const,
      free: false,
      sort: 'newest' as const,
      page: 4,
    };
    expect(parseCatalogueSearch(`?${serializeCatalogueSearch(filters)}`)).toEqual(filters);
  });
});

describe('hasActiveFilters', () => {
  it('detects a query, a filter or a non-default sort', () => {
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, q: 'photo' })).toBe(true);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, level: 'BEGINNER' })).toBe(true);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, sort: 'rating' })).toBe(true);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, page: 2 })).toBe(false);
  });
});
