import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PublicCourseDetail } from '../api/catalogue';
import { ApiError } from '../api/errors';
import type { PurchaseInfoResponse } from '../api/learner';
import { fr } from '../i18n/fr';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { CoursePage } from './CoursePage';

const state = vi.hoisted(() => ({
  course: null as unknown as PublicCourseDetail,
  purchase: null as unknown as PurchaseInfoResponse,
  pendingPayment: false,
  courseError: null as unknown | null,
  enroll: vi.fn(),
}));

const course: PublicCourseDetail = {
  id: 7,
  title: 'Étalonnage et colorimétrie',
  summary: 'Apprendre à étalonner un écran.',
  description: '<script>alert("xss")</script> Ce cours explique la colorimétrie.',
  level: 'BEGINNER',
  language: 'fr',
  price: 1200,
  effectivePrice: 1200,
  currency: 'MAD',
  discountActive: false,
  discountEndsAt: null,
  ratingAvg: 0,
  ratingCount: 0,
  studentCount: 42,
  viewCount: 100,
  instructorId: 2,
  instructorName: 'Nadia Créatrice',
  publisherName: 'Atelier Lumière',
  publisherSlug: 'atelier-lumiere',
  publisherType: 'ORGANIZATION',
  categorySlug: 'image',
  categoryName: 'Image',
  skills: [],
  requirements: [],
  tags: [],
  modules: [],
};

const purchase: PurchaseInfoResponse = {
  courseId: 7,
  title: course.title,
  price: 1200,
  effectivePrice: 1200,
  currency: 'MAD',
  discountActive: false,
  discountEndsAt: null,
  publisherName: 'Atelier Lumière',
  alreadyEnrolled: false,
  ownPublisher: false,
  free: false,
};

vi.mock('../hooks/catalogue', () => ({
  usePublicCourse: () => ({
    data: state.courseError ? undefined : state.course,
    isPending: false,
    isError: state.courseError !== null,
    error: state.courseError,
    refetch: vi.fn(),
  }),
  useCourseReviews: () => ({
    data: {
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      ratingAvg: 0,
      ratingCount: 0,
    },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

vi.mock('../hooks/learner', () => ({
  usePurchaseInfo: () => ({
    data: state.purchase,
    isPending: false,
    isError: false,
  }),
  useEnrollFree: () => ({ mutate: state.enroll, isPending: false }),
  useMyReview: () => ({ data: null, isError: false }),
  useSaveReview: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('../hooks/payments', () => ({
  useMyOpenPayment: () => ({
    data: state.pendingPayment ? { id: 99 } : null,
  }),
  useSubmitPayment: () => ({ mutate: vi.fn(), isPending: false }),
}));

beforeEach(() => {
  state.course = course;
  state.purchase = purchase;
  state.pendingPayment = false;
  state.courseError = null;
  state.enroll.mockReset();
  setAuthUser(studentUser);
});

/** The page reads its id from the route, so it needs a real route match. */
function renderCoursePage() {
  return renderWithProviders(
    <Routes>
      <Route path="/courses/:courseId" element={<CoursePage />} />
    </Routes>,
    '/courses/7',
  );
}

describe('CoursePage', () => {
  it('renders the 404 state with the page h1', () => {
    state.courseError = new ApiError(404, null);
    renderCoursePage();

    expect(
      screen.getByRole('heading', { level: 1, name: fr.coursePage.notFoundTitle }),
    ).toBeInTheDocument();
  });

  it('renders a description as text, never as HTML', () => {
    const { container } = renderCoursePage();

    expect(
      screen.getByText('<script>alert("xss")</script> Ce cours explique la colorimétrie.'),
    ).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
  });

  it('shows the buy action for a paid course the learner does not own', () => {
    renderCoursePage();
    expect(screen.getByRole('button', { name: fr.coursePage.priceCard.buy })).toBeDisabled();
    expect(screen.getByText(fr.coursePage.priceCard.buySoon)).toBeInTheDocument();
  });

  it('shows the free enrollment action for a free course', () => {
    state.course = { ...course, price: 0, effectivePrice: 0 };
    state.purchase = { ...purchase, price: 0, effectivePrice: 0, free: true };
    renderCoursePage();
    expect(
      screen.getByRole('button', { name: fr.coursePage.priceCard.enroll }),
    ).toBeInTheDocument();
  });

  it('asks an anonymous visitor to log in instead of enrolling', () => {
    setAuthUser(null);
    renderCoursePage();
    expect(
      screen.getByRole('button', { name: fr.coursePage.priceCard.loginToEnroll }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: fr.coursePage.priceCard.buy }),
    ).not.toBeInTheDocument();
  });

  it('marks an enrolled learner and links to the learner area', () => {
    state.purchase = { ...purchase, alreadyEnrolled: true };
    renderCoursePage();

    expect(screen.getByText(fr.coursePage.priceCard.enrolled)).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: fr.coursePage.priceCard.openMyCourses }),
    ).toHaveAttribute('href', '/learn/courses');
  });

  it('waits for the payment review instead of offering a second purchase', () => {
    state.pendingPayment = true;
    renderCoursePage();

    expect(screen.getByText(fr.coursePage.priceCard.pending)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: fr.coursePage.priceCard.buy }),
    ).not.toBeInTheDocument();
  });

  it('warns that a course of the caller own publisher cannot be bought', () => {
    state.purchase = { ...purchase, ownPublisher: true };
    renderCoursePage();

    expect(screen.getByText(fr.coursePage.priceCard.ownPublisher)).toBeInTheDocument();
  });
});
