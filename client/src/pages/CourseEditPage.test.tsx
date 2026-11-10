import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CourseDetail } from '../api/courses';
import { ApiError } from '../api/errors';
import {
  useAddLesson,
  useAddModule,
  useArchiveCourse,
  useCourse,
  usePublishCourse,
  useUpdateCourse,
} from '../hooks/courses';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { renderWithProviders } from '../test/utils';
import { CourseEditPage } from './CourseEditPage';

vi.mock('../hooks/courses', () => ({
  useCourse: vi.fn(),
  useUpdateCourse: vi.fn(),
  usePublishCourse: vi.fn(),
  useArchiveCourse: vi.fn(),
  useAddModule: vi.fn(),
  useAddLesson: vi.fn(),
}));

function courseWithTitle(title: string): CourseDetail {
  return {
    id: 7,
    title,
    description: 'Description existante',
    price: 349,
    currency: 'MAD',
    status: 'DRAFT',
    instructorId: 100,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    version: 3,
    modules: [],
  };
}

const idleMutation = {
  isPending: false,
  mutate: vi.fn(),
} as unknown as ReturnType<typeof useUpdateCourse>;

function mockCourse(course: CourseDetail) {
  vi.mocked(useCourse).mockReturnValue({
    isPending: false,
    isError: false,
    data: course,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useCourse>);
}

function renderEditor() {
  return renderWithProviders(
    <Routes>
      <Route path="/manage/:courseId/edit" element={<CourseEditPage />} />
    </Routes>,
    '/manage/7/edit',
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'token',
    user: {
      id: 100,
      email: 'instructor@atlas.local',
      fullName: 'Atlas Instructor',
      role: 'INSTRUCTOR',
      tenantId: '11111111-1111-1111-1111-111111111111',
      tenantSlug: 'atlas',
    },
    status: 'authenticated',
    sessionStartedAt: null,
  });
  vi.mocked(useUpdateCourse).mockReturnValue(idleMutation);
  vi.mocked(usePublishCourse).mockReturnValue(idleMutation as never);
  vi.mocked(useArchiveCourse).mockReturnValue(idleMutation as never);
  vi.mocked(useAddModule).mockReturnValue(idleMutation as never);
  vi.mocked(useAddLesson).mockReturnValue(idleMutation as never);
});

describe('CourseEditPage', () => {
  it('refuses a title shorter than three characters before sending the version', async () => {
    mockCourse(courseWithTitle('AB'));
    const user = userEvent.setup();
    renderEditor();

    expect(await screen.findByDisplayValue('AB')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: fr.editor.save }));

    expect(await screen.findByText(fr.editor.validation.titleLength)).toBeInTheDocument();
    expect(idleMutation.mutate).not.toHaveBeenCalled();
  });

  it('shows the French conflict message when the version is stale', async () => {
    mockCourse(courseWithTitle('Montage vidéo : les bases'));
    const mutate = vi.fn(
      (_input: unknown, options?: { onError?: (error: unknown) => void }) => {
        options?.onError?.(new ApiError(409, null));
      },
    );
    vi.mocked(useUpdateCourse).mockReturnValue({
      isPending: false,
      mutate,
    } as unknown as ReturnType<typeof useUpdateCourse>);

    const user = userEvent.setup();
    renderEditor();

    expect(await screen.findByDisplayValue('Montage vidéo : les bases')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: fr.editor.save }));

    expect(await screen.findByText(fr.editor.conflict)).toBeInTheDocument();
  });
});
