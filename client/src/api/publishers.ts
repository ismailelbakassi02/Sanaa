import { http } from './http';
import type {
  CreatePublisherPayload,
  CreatedPublisherResponse,
  PublicInstructorResponse,
  PublicPublisherResponse,
  PublisherProfileResponse,
  SlugAvailabilityResponse,
  UpdatePublisherPayload,
} from './types';

export async function checkSlug(slug: string): Promise<SlugAvailabilityResponse> {
  const response = await http.get<SlugAvailabilityResponse>('/api/auth/publishers/slug-available', {
    params: { slug },
  });
  return response.data;
}

/**
 * Creates a publisher either with owner data (anonymous call) or for the
 * logged-in learner (no owner fields). The response carries no token: the
 * caller logs in or refreshes to receive the publisher claims.
 */
export async function createPublisher(
  payload: CreatePublisherPayload,
): Promise<CreatedPublisherResponse> {
  const response = await http.post<CreatedPublisherResponse>('/api/auth/publishers', payload);
  return response.data;
}

export async function updateMyPublisher(
  payload: UpdatePublisherPayload,
): Promise<PublisherProfileResponse> {
  const response = await http.put<PublisherProfileResponse>('/api/auth/publishers/me', payload);
  return response.data;
}

export async function fetchPublicPublisher(slug: string): Promise<PublicPublisherResponse> {
  const response = await http.get<PublicPublisherResponse>(
    `/api/public/publishers/${encodeURIComponent(slug)}`,
  );
  return response.data;
}

export async function fetchPublicInstructor(userId: number): Promise<PublicInstructorResponse> {
  const response = await http.get<PublicInstructorResponse>(
    `/api/public/instructors/${encodeURIComponent(String(userId))}`,
  );
  return response.data;
}
