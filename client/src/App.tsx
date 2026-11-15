import { Route, Routes } from 'react-router-dom';

import type { Role } from './api/types';
import { useSessionRestore } from './auth/useSessionRestore';
import { AppLayout } from './components/AppLayout';
import { ForcedPasswordGate } from './components/ForcedPasswordGate';
import { LoadingScreen } from './components/LoadingScreen';
import { PublicLayout } from './components/PublicLayout';
import { RequireRole } from './components/RequireRole';
import { fr } from './i18n/fr';
import { AdminPage } from './pages/AdminPage';
import { AdminPaymentsPage } from './pages/AdminPaymentsPage';
import { BecomeCreatorPage } from './pages/BecomeCreatorPage';
import { CataloguePage } from './pages/CataloguePage';
import { CourseCreatePage } from './pages/CourseCreatePage';
import { CourseDetailPage } from './pages/CourseDetailPage';
import { CourseEditPage } from './pages/CourseEditPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { ManageCoursesPage } from './pages/ManageCoursesPage';
import { MePage } from './pages/MePage';
import { MyCoursesPage } from './pages/MyCoursesPage';
import { MyPaymentsPage } from './pages/MyPaymentsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PasswordPage } from './pages/PasswordPage';
import { PrivacyPage } from './pages/PrivacyPage';
import { PublicInstructorPage } from './pages/PublicInstructorPage';
import { PublicPublisherPage } from './pages/PublicPublisherPage';
import { RegisterPage } from './pages/RegisterPage';
import { StudioProfilePage } from './pages/StudioProfilePage';
import { StudioTeamPage } from './pages/StudioTeamPage';
import { useAuthStore } from './store/auth';

/**
 * Course pages are kept mounted but hidden from every menu until the
 * marketplace tasks. They keep their v1 role rules so they cannot be used as a
 * back door while they wait.
 */
const CONTENT_ROLES = ['STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN'] as const satisfies readonly Role[];
const STUDENT_ROLES = ['STUDENT'] as const satisfies readonly Role[];
const STUDIO_ROLES = ['TENANT_ADMIN', 'INSTRUCTOR'] as const satisfies readonly Role[];
const ADMIN_ROLES = ['TENANT_ADMIN'] as const satisfies readonly Role[];
const AUTHORING_ROLES = ['INSTRUCTOR', 'TENANT_ADMIN'] as const satisfies readonly Role[];
const PLATFORM_ADMIN_ROLES = ['SUPER_ADMIN'] as const satisfies readonly Role[];

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="become-creator" element={<BecomeCreatorPage />} />
        <Route path="publishers/:slug" element={<PublicPublisherPage />} />
        <Route path="instructors/:id" element={<PublicInstructorPage />} />
        <Route path="privacy" element={<PrivacyPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      <Route
        element={
          <RequireRole>
            <ForcedPasswordGate>
              <AppLayout />
            </ForcedPasswordGate>
          </RequireRole>
        }
      >
        <Route path="me" element={<MePage />} />
        <Route path="password" element={<PasswordPage />} />
        <Route
          path="studio/profile"
          element={
            <RequireRole roles={STUDIO_ROLES}>
              <StudioProfilePage />
            </RequireRole>
          }
        />
        <Route
          path="studio/team"
          element={
            <RequireRole roles={ADMIN_ROLES}>
              <StudioTeamPage />
            </RequireRole>
          }
        />
        <Route
          path="admin"
          element={
            <RequireRole roles={PLATFORM_ADMIN_ROLES}>
              <AdminPage />
            </RequireRole>
          }
        />

        {/* Hidden until the marketplace tasks: reachable by URL, absent from menus. */}
        <Route
          path="courses"
          element={
            <RequireRole roles={CONTENT_ROLES}>
              <CataloguePage />
            </RequireRole>
          }
        />
        <Route
          path="courses/:courseId"
          element={
            <RequireRole roles={CONTENT_ROLES}>
              <CourseDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="my-courses"
          element={
            <RequireRole roles={STUDENT_ROLES}>
              <MyCoursesPage />
            </RequireRole>
          }
        />
        <Route
          path="my-payments"
          element={
            <RequireRole roles={STUDENT_ROLES}>
              <MyPaymentsPage />
            </RequireRole>
          }
        />
        <Route
          path="payments"
          element={
            <RequireRole roles={ADMIN_ROLES}>
              <AdminPaymentsPage />
            </RequireRole>
          }
        />
        <Route
          path="manage"
          element={
            <RequireRole roles={AUTHORING_ROLES}>
              <ManageCoursesPage />
            </RequireRole>
          }
        />
        <Route
          path="manage/new"
          element={
            <RequireRole roles={AUTHORING_ROLES}>
              <CourseCreatePage />
            </RequireRole>
          }
        />
        <Route
          path="manage/:courseId/edit"
          element={
            <RequireRole roles={AUTHORING_ROLES}>
              <CourseEditPage />
            </RequireRole>
          }
        />
      </Route>
    </Routes>
  );
}

export function Root() {
  useSessionRestore();
  const status = useAuthStore((state) => state.status);

  if (status === 'loading') {
    return <LoadingScreen message={fr.common.loading} />;
  }

  return <App />;
}
