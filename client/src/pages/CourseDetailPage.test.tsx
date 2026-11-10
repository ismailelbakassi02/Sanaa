import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CourseDetail } from '../api/courses';
import { fr } from '../i18n/fr';
import { useCourse } from '../hooks/courses';
import { useMyEnrollments } from '../hooks/enrollments';
import { useAuthStore } from '../store/auth';
import { renderWithProviders, studentUser } from '../test/utils';
import { CourseDetailPage } from './CourseDetailPage';

vi.mock('../hooks/courses', () => ({ useCourse: vi.fn() }));
vi.mock('../hooks/enrollments', () => ({ useMyEnrollments: vi.fn() }));

const course: CourseDetail = {
  id: 7,
  title: 'Montage vidéo : les bases',
  description: 'Apprenez à monter une vidéo de bout en bout.',
  price: 349,
  currency: 'MAD',
  status: 'PUBLISHED',
  instructorId: 100,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  version: 3,
  modules: [
    {
      id: 11,
      title: 'Préparer son projet',
      position: 1,
      lessons: [
        {
          id: 21,
          title: 'Organiser ses rushes',
          position: 1,
          durationSeconds: 420,
          freePreview: true,
        },
      ],
    },
  ],
};

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'token',
    user: studentUser,
    status: 'authenticated',
    sessionStartedAt: null,
  });
  vi.mocked(useCourse).mockReturnValue({
    isPending: false,
    isError: false,
    data: course,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useCourse>);
  vi.mocked(useMyEnrollments).mockReturnValue({
    isPending: false,
    isError: false,
    data: [],
  } as unknown as ReturnType<typeof useMyEnrollments>);
});

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/courses/:courseId" element={<CourseDetailPage />} />
    </Routes>,
    '/courses/7',
  );
}

describe('CourseDetailPage', () => {
  it('shows a disabled buy button and the payment notice for a student who is not enrolled', async () => {
    renderDetail();

    expect(await screen.findByText(fr.courseDetail.buy)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: fr.courseDetail.buy })).toBeDisabled();
    expect(screen.getByText(fr.courseDetail.paymentSoon)).toBeInTheDocument();
  });

  it('renders the curriculum with lesson duration and free preview', async () => {
    renderDetail();

    expect(await screen.findByText('1. Préparer son projet')).toBeInTheDocument();
    expect(screen.getByText('Organiser ses rushes')).toBeInTheDocument();
    expect(screen.getByText('07:00')).toBeInTheDocument();
    expect(screen.getByText(fr.courseDetail.freePreview)).toBeInTheDocument();
  });

  it('shows the enrolled tag instead of the buy button when the student is enrolled', async () => {
    vi.mocked(useMyEnrollments).mockReturnValue({
      isPending: false,
      isError: false,
      data: [{ id: 1, status: 'ACTIVE', enrolledAt: '2026-11-02T10:00:00Z', course }],
    } as unknown as ReturnType<typeof useMyEnrollments>);

    renderDetail();

    expect(await screen.findByText(fr.courseDetail.enrolled)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: fr.courseDetail.buy })).not.toBeInTheDocument();
  });
});
