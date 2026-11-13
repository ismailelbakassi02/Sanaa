import { useQuery } from '@tanstack/react-query';

import { fetchMyEnrollments } from '../api/enrollments';

export const enrollmentKeys = {
  mine: ['enrollments', 'me'] as const,
};

/**
 * Enrollments are only exposed to students. Callers pass `enabled` so an
 * instructor or administrator never triggers a guaranteed 403.
 */
export function useMyEnrollments(enabled = true) {
  return useQuery({
    queryKey: enrollmentKeys.mine,
    queryFn: fetchMyEnrollments,
    enabled,
    // Enrollments arrive asynchronously from the payment-approved event. A
    // student who just paid should see the course after switching back to the
    // tab, without reloading the whole page.
    refetchOnWindowFocus: true,
    refetchOnMount: 'always',
  });
}
