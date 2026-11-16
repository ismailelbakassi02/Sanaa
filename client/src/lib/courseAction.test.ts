import { describe, expect, it } from 'vitest';

import { courseAction, type CourseActionInput } from './courseAction';

const base: CourseActionInput = {
  authenticated: true,
  enrolled: false,
  ownPublisher: false,
  free: false,
  pendingPayment: false,
};

describe('courseAction', () => {
  it('asks an anonymous visitor to log in, whatever the course costs', () => {
    expect(courseAction({ ...base, authenticated: false, free: true })).toBe('login');
  });

  it('never offers a second action to an enrolled learner', () => {
    expect(courseAction({ ...base, enrolled: true, free: true })).toBe('enrolled');
  });

  it('refuses a course of the caller own publisher', () => {
    expect(courseAction({ ...base, ownPublisher: true, free: true })).toBe('own');
  });

  it('waits for the payment review instead of buying twice', () => {
    expect(courseAction({ ...base, pendingPayment: true })).toBe('pending');
  });

  it('enrolls in a free course and buys a paid one', () => {
    expect(courseAction({ ...base, free: true })).toBe('enroll');
    expect(courseAction(base)).toBe('buy');
  });

  it('shows no action when the trusted purchase info is unavailable', () => {
    expect(courseAction({ ...base, purchaseUnavailable: true, free: true })).toBe('unavailable');
  });
});
