import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { catalogueKeys } from './catalogue';
import {
  createReview,
  enrollFree,
  fetchLearnerHome,
  fetchMyEnrollments,
  fetchMyReview,
  fetchPurchaseInfo,
  updateReview,
  type ReviewPayload,
} from '../api/learner';

export const learnerKeys = {
  all: ['learner'] as const,
  home: ['learner', 'home'] as const,
  enrollments: ['learner', 'enrollments'] as const,
  purchaseInfo: (courseId: number) => ['learner', 'purchase-info', courseId] as const,
  myReview: (courseId: number) => ['learner', 'my-review', courseId] as const,
};

export function useLearnerHome(enabled = true) {
  return useQuery({
    queryKey: learnerKeys.home,
    queryFn: fetchLearnerHome,
    enabled,
  });
}

export function useMyEnrollments(enabled = true) {
  return useQuery({
    queryKey: learnerKeys.enrollments,
    queryFn: fetchMyEnrollments,
    enabled,
  });
}

/**
 * The trusted price and eligibility of a course for the signed-in caller.
 * It needs a token, hence the explicit enabled flag.
 */
export function usePurchaseInfo(courseId: number | null, enabled = true) {
  return useQuery({
    queryKey: learnerKeys.purchaseInfo(courseId ?? 0),
    queryFn: () => fetchPurchaseInfo(courseId as number),
    enabled: enabled && courseId !== null,
    retry: false,
  });
}

export function useMyReview(courseId: number | null, enabled = true) {
  return useQuery({
    queryKey: learnerKeys.myReview(courseId ?? 0),
    queryFn: () => fetchMyReview(courseId as number),
    enabled: enabled && courseId !== null,
    retry: false,
  });
}

export function useEnrollFree(courseId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => enrollFree(courseId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: learnerKeys.all });
      void queryClient.invalidateQueries({ queryKey: catalogueKeys.all });
    },
  });
}

/**
 * A review is an upsert: POST creates it, PUT edits it. Either way, the
 * aggregate, the review page and the learner dashboard all become stale.
 */
export function useSaveReview(courseId: number, isUpdate: boolean) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReviewPayload) =>
      isUpdate ? updateReview(courseId, payload) : createReview(courseId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: learnerKeys.all });
      void queryClient.invalidateQueries({ queryKey: catalogueKeys.all });
    },
  });
}
