import { useQuery } from '@tanstack/react-query';

import { fetchGoogleOAuthStatus } from '../api/auth';

export const authKeys = {
  googleStatus: ['auth', 'google', 'status'] as const,
};

/** Public feature discovery: normal login remains visible even when Google is unavailable. */
export function useGoogleOAuthStatus() {
  return useQuery({
    queryKey: authKeys.googleStatus,
    queryFn: fetchGoogleOAuthStatus,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}
