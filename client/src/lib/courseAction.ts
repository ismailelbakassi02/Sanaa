/**
 * The course page has exactly one primary action. It is derived from the
 * trusted purchase info of the signed-in learner, never from the card price:
 * a stale page, an existing enrollment or a self-purchase must change the
 * action without a second source of truth.
 */
export interface CourseActionInput {
  authenticated: boolean;
  enrolled: boolean;
  ownPublisher: boolean;
  free: boolean;
  pendingPayment: boolean;
  /** The role cannot use the learner API, or the trusted lookup failed. */
  purchaseUnavailable?: boolean;
}

export type CourseActionKind =
  | 'login'
  | 'unavailable'
  | 'enrolled'
  | 'own'
  | 'pending'
  | 'enroll'
  | 'buy';

export function courseAction(input: CourseActionInput): CourseActionKind {
  if (!input.authenticated) {
    return 'login';
  }
  if (input.purchaseUnavailable) {
    return 'unavailable';
  }
  if (input.enrolled) {
    return 'enrolled';
  }
  if (input.ownPublisher) {
    return 'own';
  }
  if (input.pendingPayment) {
    return 'pending';
  }
  return input.free ? 'enroll' : 'buy';
}
