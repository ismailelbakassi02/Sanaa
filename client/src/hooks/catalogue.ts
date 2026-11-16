import { useQuery } from '@tanstack/react-query';

import {
  fetchCategories,
  fetchCourseReviews,
  fetchPublicCourse,
  fetchTrending,
  searchCourses,
  type CourseSearchParams,
} from '../api/catalogue';

export const catalogueKeys = {
  all: ['catalogue'] as const,
  categories: ['catalogue', 'categories'] as const,
  trending: (limit: number) => ['catalogue', 'trending', limit] as const,
  search: (params: CourseSearchParams) => ['catalogue', 'search', params] as const,
  course: (id: number) => ['catalogue', 'course', id] as const,
  reviews: (id: number, page: number, size: number) =>
    ['catalogue', 'reviews', id, page, size] as const,
};

export function useCategories() {
  return useQuery({
    queryKey: catalogueKeys.categories,
    queryFn: fetchCategories,
  });
}

export function useTrending(limit = 8) {
  return useQuery({
    queryKey: catalogueKeys.trending(limit),
    queryFn: () => fetchTrending(limit),
  });
}

/**
 * The public search has no publisher or instructor filter yet, so the creator
 * pages read the newest catalogue window and keep the matching cards locally.
 * The limitation is recorded in docs/adr/005-frontend.md.
 */
export const CREATOR_COURSE_WINDOW = 24;

export function useCatalogueWindow() {
  return useQuery({
    queryKey: ['catalogue', 'window', CREATOR_COURSE_WINDOW] as const,
    queryFn: () =>
      searchCourses({ page: 0, size: CREATOR_COURSE_WINDOW, sort: 'newest' }),
  });
}

/**
 * One catalogue page. Keeping the previous page visible while the next query
 * loads avoids a full-page skeleton on every filter change.
 */
export function useCourseSearch(params: CourseSearchParams) {
  return useQuery({
    queryKey: catalogueKeys.search(params),
    queryFn: () => searchCourses(params),
    placeholderData: (previous) => previous,
  });
}

export function usePublicCourse(id: number | null) {
  return useQuery({
    queryKey: catalogueKeys.course(id ?? 0),
    queryFn: () => fetchPublicCourse(id as number),
    enabled: id !== null,
    retry: false,
  });
}

export function useCourseReviews(id: number | null, page: number, size: number) {
  return useQuery({
    queryKey: catalogueKeys.reviews(id ?? 0, page, size),
    queryFn: () => fetchCourseReviews(id as number, page, size),
    enabled: id !== null,
    placeholderData: (previous) => previous,
  });
}
