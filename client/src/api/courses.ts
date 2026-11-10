import { http } from './http';

/**
 * Typed mirror of course-service/web/dto. Keep this file in sync with the Java
 * records: the payload shapes are the contract, not a guess.
 */

export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface CourseSummary {
  id: number;
  title: string;
  description: string | null;
  /** BigDecimal serialized by Jackson as a JSON number. */
  price: number;
  currency: string;
  status: CourseStatus;
  instructorId: number;
  createdAt: string;
  updatedAt: string;
}

export interface LessonSummary {
  id: number;
  title: string;
  position: number;
  durationSeconds: number;
  freePreview: boolean;
}

export interface ModuleSummary {
  id: number;
  title: string;
  position: number;
  lessons: LessonSummary[];
}

export interface CourseDetail extends CourseSummary {
  version: number;
  modules: ModuleSummary[];
}

/** Mirrors PageResponse<T> from course-service. */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CourseInput {
  title: string;
  description: string | null;
  price: number;
  currency: 'MAD';
}

export interface UpdateCourseInput extends CourseInput {
  /** Optimistic-locking token returned by the last read. */
  version: number;
}

export interface AddModuleInput {
  title: string;
}

export interface AddLessonInput {
  title: string;
  durationSeconds: number;
  freePreview: boolean;
}

export interface ModuleCreated {
  id: number;
  courseId: number;
  title: string;
  position: number;
}

export interface LessonCreated {
  id: number;
  courseId: number;
  moduleId: number;
  title: string;
  position: number;
  durationSeconds: number;
  freePreview: boolean;
}

export interface CoursePageParams {
  /** Zero-based, as the backend expects. */
  page: number;
  size: number;
}

const MAX_PAGES = 20;

export async function fetchCourses(params: CoursePageParams): Promise<PageResponse<CourseSummary>> {
  const response = await http.get<PageResponse<CourseSummary>>('/api/courses', { params });
  return response.data;
}

/**
 * Reads every page visible to the caller.
 *
 * WHY: the API has no instructor filter, so "my courses" must be filtered on
 * the client. Filtering a single page would silently hide own courses that sit
 * on a later page.
 */
export async function fetchAllCourses(pageSize = 50): Promise<CourseSummary[]> {
  const first = await fetchCourses({ page: 0, size: pageSize });
  const items = [...first.items];
  const lastPage = Math.min(first.totalPages, MAX_PAGES);
  for (let page = 1; page < lastPage; page += 1) {
    const next = await fetchCourses({ page, size: pageSize });
    items.push(...next.items);
  }
  return items;
}

export async function fetchCourse(id: number): Promise<CourseDetail> {
  const response = await http.get<CourseDetail>(`/api/courses/${id}`);
  return response.data;
}

export async function createCourse(input: CourseInput): Promise<CourseDetail> {
  const response = await http.post<CourseDetail>('/api/courses', input);
  return response.data;
}

export async function updateCourse(id: number, input: UpdateCourseInput): Promise<CourseDetail> {
  const response = await http.put<CourseDetail>(`/api/courses/${id}`, input);
  return response.data;
}

export async function publishCourse(id: number): Promise<CourseDetail> {
  const response = await http.patch<CourseDetail>(`/api/courses/${id}/publish`);
  return response.data;
}

export async function archiveCourse(id: number): Promise<CourseDetail> {
  const response = await http.patch<CourseDetail>(`/api/courses/${id}/archive`);
  return response.data;
}

export async function addModule(courseId: number, input: AddModuleInput): Promise<ModuleCreated> {
  const response = await http.post<ModuleCreated>(`/api/courses/${courseId}/modules`, input);
  return response.data;
}

export async function addLesson(moduleId: number, input: AddLessonInput): Promise<LessonCreated> {
  const response = await http.post<LessonCreated>(`/api/modules/${moduleId}/lessons`, input);
  return response.data;
}
