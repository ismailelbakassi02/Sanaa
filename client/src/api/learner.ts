import type {
  CategoryResponse,
  PublicCourseCard,
} from './catalogue';
import { http } from './http';
import type { EnrollmentSource, ReviewStatus } from './types';

/**
 * Typed mirror of course-service/web/dto/learner. Every read is scoped to the
 * JWT subject server-side; no learner id ever travels in a request.
 */

export interface LearnerEnrollmentResponse {
  enrollmentId: number;
  enrolledAt: string;
  source: EnrollmentSource;
  course: PublicCourseCard;
}

export interface LearnerTeacherResponse {
  instructorId: number;
  instructorName: string;
  publisherName: string;
  publisherSlug: string;
}

export interface LearnerHomeResponse {
  myCourses: LearnerEnrollmentResponse[];
  myTeachers: LearnerTeacherResponse[];
  suggestedCategories: CategoryResponse[];
  trending: PublicCourseCard[];
  categories: CategoryResponse[];
}

export interface MyReviewResponse {
  id: number;
  courseId: number;
  rating: number;
  comment: string | null;
  status: ReviewStatus;
  createdAt: string;
  updatedAt: string;
  publisherReply: string | null;
  repliedAt: string | null;
}

export interface ReviewPayload {
  rating: number;
  comment: string | null;
}

export interface PurchaseInfoResponse {
  courseId: number;
  title: string;
  price: number;
  effectivePrice: number;
  currency: string;
  discountActive: boolean;
  discountEndsAt: string | null;
  publisherName: string;
  alreadyEnrolled: boolean;
  ownPublisher: boolean;
  free: boolean;
}

export async function fetchLearnerHome(): Promise<LearnerHomeResponse> {
  const response = await http.get<LearnerHomeResponse>('/api/learner/home');
  return response.data;
}

export async function fetchMyEnrollments(): Promise<LearnerEnrollmentResponse[]> {
  const response = await http.get<LearnerEnrollmentResponse[]>('/api/learner/enrollments');
  return response.data;
}

export async function fetchPurchaseInfo(courseId: number): Promise<PurchaseInfoResponse> {
  const response = await http.get<PurchaseInfoResponse>(
    `/api/learner/courses/${courseId}/purchase-info`,
  );
  return response.data;
}

/**
 * The endpoint answers 404 when the caller has not reviewed the course yet.
 * That is a normal state for the review form, not an error, so it maps to null.
 */
export async function fetchMyReview(courseId: number): Promise<MyReviewResponse | null> {
  const response = await http.get<MyReviewResponse>(
    `/api/learner/courses/${courseId}/my-review`,
    { validateStatus: (status) => status === 200 || status === 404 },
  );
  return response.status === 404 ? null : response.data;
}

export async function enrollFree(courseId: number): Promise<LearnerEnrollmentResponse> {
  const response = await http.post<LearnerEnrollmentResponse>(`/api/courses/${courseId}/enroll`);
  return response.data;
}

export async function createReview(
  courseId: number,
  payload: ReviewPayload,
): Promise<MyReviewResponse> {
  const response = await http.post<MyReviewResponse>(
    `/api/courses/${courseId}/reviews`,
    payload,
  );
  return response.data;
}

export async function updateReview(
  courseId: number,
  payload: ReviewPayload,
): Promise<MyReviewResponse> {
  const response = await http.put<MyReviewResponse>(`/api/courses/${courseId}/reviews`, payload);
  return response.data;
}
