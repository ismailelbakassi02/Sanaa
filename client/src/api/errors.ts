import { isAxiosError } from 'axios';

import type { ProblemDetail } from './types';

/**
 * Typed representation of an API failure. The ProblemDetail body is kept for
 * diagnostics, but the UI only displays the French message from i18n/fr.ts.
 */
export class ApiError extends Error {
  readonly status: number;

  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (isAxiosError<ProblemDetail>(error)) {
    return new ApiError(error.response?.status ?? 0, error.response?.data ?? null);
  }
  return new ApiError(0, null);
}
