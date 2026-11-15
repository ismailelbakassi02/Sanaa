import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  checkSlug,
  createPublisher,
  fetchPublicInstructor,
  fetchPublicPublisher,
  updateMyPublisher,
} from '../api/publishers';
import type {
  CreatePublisherPayload,
  PublicInstructorResponse,
  PublicPublisherResponse,
  PublisherProfileResponse,
  UpdatePublisherPayload,
} from '../api/types';
import { isValidSlug, normalizeSlug } from '../lib/slug';
import { useDebouncedValue } from './useDebouncedValue';

export const publisherKeys = {
  public: (slug: string) => ['publisher', 'public', slug] as const,
  instructor: (userId: number) => ['publisher', 'instructor', userId] as const,
  slug: (slug: string) => ['publisher', 'slug', slug] as const,
};

export function usePublicPublisher(slug: string | null | undefined) {
  return useQuery<PublicPublisherResponse>({
    queryKey: publisherKeys.public(slug ?? ''),
    queryFn: () => fetchPublicPublisher(slug ?? ''),
    enabled: Boolean(slug),
    retry: false,
  });
}

export function usePublicInstructor(userId: number | null | undefined) {
  return useQuery<PublicInstructorResponse>({
    queryKey: publisherKeys.instructor(userId ?? 0),
    queryFn: () => fetchPublicInstructor(userId ?? 0),
    enabled: typeof userId === 'number' && Number.isFinite(userId),
    retry: false,
  });
}

/**
 * Availability is only requested for a syntactically valid, non-reserved slug.
 * The debounce keeps typing from spending the public rate-limit budget.
 */
export function useSlugAvailability(slug: string) {
  const normalized = normalizeSlug(slug);
  const debounced = useDebouncedValue(normalized, 350);
  const canCheck = isValidSlug(debounced);
  const query = useQuery({
    queryKey: publisherKeys.slug(debounced),
    queryFn: () => checkSlug(debounced),
    enabled: canCheck,
    retry: false,
  });

  return {
    isChecking: canCheck && (query.isPending || debounced !== normalized),
    available: canCheck ? (query.data?.available ?? null) : null,
    failed: query.isError,
  };
}

export function useCreatePublisher() {
  return useMutation({
    mutationFn: (payload: CreatePublisherPayload) => createPublisher(payload),
  });
}

export function useUpdatePublisher() {
  const queryClient = useQueryClient();
  return useMutation<PublisherProfileResponse, unknown, UpdatePublisherPayload>({
    mutationFn: (payload) => updateMyPublisher(payload),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: publisherKeys.public(updated.slug) });
    },
  });
}
