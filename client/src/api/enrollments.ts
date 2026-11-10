import { http } from './http';
import type { CourseSummary } from './courses';

export type EnrollmentStatus = 'ACTIVE' | 'REVOKED';

/** Mirrors MyEnrollmentResponse from course-service. */
export interface MyEnrollment {
  id: number;
  status: EnrollmentStatus;
  enrolledAt: string;
  course: CourseSummary;
}

/**
 * GET /api/enrollments/me is STUDENT-only and read-only: enrollments are
 * created by the payment-approved event consumer, not by the client.
 */
export async function fetchMyEnrollments(): Promise<MyEnrollment[]> {
  const response = await http.get<MyEnrollment[]>('/api/enrollments/me');
  return response.data;
}
