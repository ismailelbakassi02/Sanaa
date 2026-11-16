import { COURSE_SORTS, type CourseSort } from '../api/catalogue';
import type { CourseLevel } from '../api/types';

/**
 * The catalogue state lives in the URL, so a filtered search can be shared,
 * bookmarked and restored by the browser back button. One parser and one
 * serializer keep the wire format in a single place.
 */
export interface CatalogueFilters {
  q: string;
  category: string;
  tag: string;
  level: CourseLevel | '';
  free: boolean;
  sort: CourseSort;
  /** One-based in the URL, zero-based on the wire. */
  page: number;
}

export const DEFAULT_FILTERS: CatalogueFilters = {
  q: '',
  category: '',
  tag: '',
  level: '',
  free: false,
  sort: 'trending',
  page: 1,
};

const LEVELS: readonly CourseLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

function single(params: URLSearchParams, key: string): string {
  return (params.get(key) ?? '').trim();
}

function validLevel(value: string): CourseLevel | '' {
  return LEVELS.includes(value as CourseLevel) ? (value as CourseLevel) : '';
}

function validSort(value: string): CourseSort {
  return COURSE_SORTS.includes(value as CourseSort) ? (value as CourseSort) : 'trending';
}

function validPage(value: string): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

export function parseCatalogueSearch(search: string): CatalogueFilters {
  const params = new URLSearchParams(search);
  return {
    q: single(params, 'q'),
    category: single(params, 'category'),
    tag: single(params, 'tag'),
    level: validLevel(single(params, 'level')),
    free: single(params, 'free') === 'true',
    sort: validSort(single(params, 'sort')),
    page: validPage(single(params, 'page')),
  };
}

/** Stable parameter order; defaults are omitted so the address stays short. */
export function serializeCatalogueSearch(filters: CatalogueFilters): string {
  const params = new URLSearchParams();
  if (filters.q) {
    params.set('q', filters.q);
  }
  if (filters.category) {
    params.set('category', filters.category);
  }
  if (filters.tag) {
    params.set('tag', filters.tag);
  }
  if (filters.level) {
    params.set('level', filters.level);
  }
  if (filters.free) {
    params.set('free', 'true');
  }
  if (filters.sort !== 'trending') {
    params.set('sort', filters.sort);
  }
  if (filters.page > 1) {
    params.set('page', String(filters.page));
  }
  return params.toString();
}

export function hasActiveFilters(filters: CatalogueFilters): boolean {
  return Boolean(
    filters.q ||
      filters.category ||
      filters.tag ||
      filters.level ||
      filters.free ||
      filters.sort !== 'trending',
  );
}
