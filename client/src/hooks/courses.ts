import { useMutation, useQuery, useQueryClient, useQueries } from '@tanstack/react-query';

import {
  addLesson,
  addModule,
  archiveCourse,
  createCourse,
  fetchAllCourses,
  fetchCourse,
  fetchCourses,
  publishCourse,
  updateCourse,
  type AddLessonInput,
  type AddModuleInput,
  type CourseDetail,
  type CourseInput,
  type CourseSummary,
  type UpdateCourseInput,
} from '../api/courses';
import { useAuthStore } from '../store/auth';

/** The catalogue shows twelve courses per page. */
export const CATALOGUE_PAGE_SIZE = 12;

export const courseKeys = {
  /** Prefix shared by every list query, used for invalidation. */
  lists: ['courses'] as const,
  list: (page: number) => ['courses', 'list', page] as const,
  all: ['courses', 'all'] as const,
  detail: (id: number) => ['course', id] as const,
};

/** One paginated catalogue page, one-based in the UI and zero-based on the wire. */
export function useCatalogue(page: number) {
  return useQuery({
    queryKey: courseKeys.list(page),
    queryFn: () => fetchCourses({ page: page - 1, size: CATALOGUE_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

export function useCourse(id: number | null) {
  return useQuery({
    queryKey: courseKeys.detail(id ?? 0),
    queryFn: () => fetchCourse(id as number),
    enabled: id !== null,
  });
}

/**
 * Courses the caller may manage. Instructors only own the courses they
 * created; tenant administrators manage every course of the tenant.
 *
 * The API returns "own OR published" to instructors, so the ownership filter
 * is applied here, after loading every page.
 */
export function useManagedCourses() {
  const user = useAuthStore((state) => state.user);
  return useQuery({
    queryKey: [...courseKeys.all, user?.role ?? 'anonymous', user?.id ?? 0],
    queryFn: () => fetchAllCourses(),
    enabled: user !== null,
    select: (courses: CourseSummary[]) =>
      user?.role === 'INSTRUCTOR'
        ? courses.filter((course) => course.instructorId === user.id)
        : courses,
  });
}

function useCourseSaved() {
  const queryClient = useQueryClient();
  return (course: CourseDetail) => {
    queryClient.setQueryData(courseKeys.detail(course.id), course);
    void queryClient.invalidateQueries({ queryKey: courseKeys.lists });
  };
}

export function useCreateCourse() {
  const onSaved = useCourseSaved();
  return useMutation({
    mutationFn: (input: CourseInput) => createCourse(input),
    onSuccess: onSaved,
  });
}

export function useUpdateCourse(id: number) {
  const onSaved = useCourseSaved();
  return useMutation({
    mutationFn: (input: UpdateCourseInput) => updateCourse(id, input),
    onSuccess: onSaved,
  });
}

export function usePublishCourse(id: number) {
  const onSaved = useCourseSaved();
  return useMutation({
    mutationFn: () => publishCourse(id),
    onSuccess: onSaved,
  });
}

export function useArchiveCourse(id: number) {
  const onSaved = useCourseSaved();
  return useMutation({
    mutationFn: () => archiveCourse(id),
    onSuccess: onSaved,
  });
}

export function useAddModule(courseId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AddModuleInput) => addModule(courseId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: courseKeys.detail(courseId) });
      void queryClient.invalidateQueries({ queryKey: courseKeys.lists });
    },
  });
}

export function useAddLesson(courseId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ moduleId, input }: { moduleId: number; input: AddLessonInput }) =>
      addLesson(moduleId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: courseKeys.detail(courseId) });
    },
  });
}

/**
 * Lesson counts for the visible table page.
 *
 * WHY: CourseSummary carries no lesson count, so the count comes from the
 * course detail. Only the current page is fetched, which keeps the management
 * table to a bounded number of requests.
 */
export function useLessonCounts(courseIds: readonly number[]): Map<number, number> {
  const results = useQueries({
    queries: courseIds.map((id) => ({
      queryKey: courseKeys.detail(id),
      queryFn: () => fetchCourse(id),
      select: (course: CourseDetail) =>
        course.modules.reduce((total, module) => total + module.lessons.length, 0),
    })),
  });

  const counts = new Map<number, number>();
  results.forEach((result, index) => {
    if (result.data !== undefined) {
      counts.set(courseIds[index], result.data);
    }
  });
  return counts;
}
