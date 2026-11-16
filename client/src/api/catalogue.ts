import { http } from './http';
import type { CourseLevel, PageResponse } from './types';

/**
 * Typed mirror of course-service/web/dto/publicapi. These payloads are the
 * anonymous catalogue contract: no email, tenant id, media path or payment id.
 */

export interface CategoryResponse {
  id: number;
  slug: string;
  name: string;
  courseCount: number;
}

export interface PublicCourseCard {
  id: number;
  title: string;
  summary: string | null;
  level: CourseLevel;
  language: string;
  price: number;
  effectivePrice: number;
  currency: string;
  discountActive: boolean;
  ratingAvg: number;
  ratingCount: number;
  studentCount: number;
  instructorName: string;
  publisherName: string;
  publisherSlug: string;
  publisherType: 'INDIVIDUAL' | 'ORGANIZATION';
  categorySlug: string;
  categoryName: string;
  tags: string[];
}

export interface PublicLessonResponse {
  id: number;
  title: string;
  description: string | null;
  position: number;
  durationSeconds: number;
  freePreview: boolean;
  available: boolean;
}

export interface PublicModuleResponse {
  id: number;
  title: string;
  position: number;
  lessons: PublicLessonResponse[];
}

export interface PublicCourseDetail extends Omit<PublicCourseCard, 'tags'> {
  description: string | null;
  discountEndsAt: string | null;
  viewCount: number;
  instructorId: number;
  skills: string[];
  requirements: string[];
  tags: string[];
  modules: PublicModuleResponse[];
}

export interface PublicReviewResponse {
  id: number;
  learnerName: string;
  rating: number;
  comment: string | null;
  publisherReply: string | null;
  repliedAt: string | null;
  createdAt: string;
}

export interface PublicReviewsResponse {
  items: PublicReviewResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  ratingAvg: number;
  ratingCount: number;
}

/** Server-accepted sort values, in the order the search page offers them. */
export const COURSE_SORTS = ['trending', 'newest', 'rating', 'price_asc', 'price_desc'] as const;
export type CourseSort = (typeof COURSE_SORTS)[number];

/** The backend caps this at 24; asking for more would silently disappoint. */
export const CATALOGUE_PAGE_SIZE = 24;

export interface CourseSearchParams {
  q?: string;
  category?: string;
  tag?: string;
  level?: CourseLevel;
  free?: boolean;
  minPrice?: number;
  maxPrice?: number;
  sort?: CourseSort;
  /** Zero-based, as the backend expects. */
  page?: number;
  size?: number;
}

export async function fetchCategories(): Promise<CategoryResponse[]> {
  const response = await http.get<CategoryResponse[]>('/api/public/categories');
  return response.data;
}

export async function searchCourses(
  params: CourseSearchParams,
): Promise<PageResponse<PublicCourseCard>> {
  const response = await http.get<PageResponse<PublicCourseCard>>('/api/public/courses', { params });
  return response.data;
}

export async function fetchTrending(limit = 8): Promise<PublicCourseCard[]> {
  const response = await http.get<PublicCourseCard[]>('/api/public/courses/trending', {
    params: { limit },
  });
  return response.data;
}

export async function fetchPublicCourse(id: number): Promise<PublicCourseDetail> {
  const response = await http.get<PublicCourseDetail>(`/api/public/courses/${id}`);
  return response.data;
}

export async function fetchCourseReviews(
  id: number,
  page = 0,
  size = 10,
): Promise<PublicReviewsResponse> {
  const response = await http.get<PublicReviewsResponse>(`/api/public/courses/${id}/reviews`, {
    params: { page, size },
  });
  return response.data;
}
