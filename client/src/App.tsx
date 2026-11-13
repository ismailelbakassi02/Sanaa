import { Route, Routes } from 'react-router-dom';

import type { Role } from './api/types';
import { useSessionRestore } from './auth/useSessionRestore';
import { AppLayout } from './components/AppLayout';
import { LoadingScreen } from './components/LoadingScreen';
import { RequireRole } from './components/RequireRole';
import { fr } from './i18n/fr';
import { AdminPaymentsPage } from './pages/AdminPaymentsPage';
import { CataloguePage } from './pages/CataloguePage';
import { CourseCreatePage } from './pages/CourseCreatePage';
import { CourseDetailPage } from './pages/CourseDetailPage';
import { CourseEditPage } from './pages/CourseEditPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { ManageCoursesPage } from './pages/ManageCoursesPage';
import { MyCoursesPage } from './pages/MyCoursesPage';
import { MyPaymentsPage } from './pages/MyPaymentsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RegisterPage } from './pages/RegisterPage';
import { useAuthStore } from './store/auth';
import { BrandingGate } from './tenant/BrandingGate';

/**
 * Course content is readable by students, instructors and tenant
 * administrators. SUPER_ADMIN is excluded on purpose: course-service rejects
 * that role with 403, so the client must not offer the routes either.
 */
const CONTENT_ROLES = ['STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN'] as const satisfies readonly Role[];
const STUDENT_ROLES = ['STUDENT'] as const satisfies readonly Role[];
const ADMIN_ROLES = ['TENANT_ADMIN'] as const satisfies readonly Role[];
const AUTHORING_ROLES = ['INSTRUCTOR', 'TENANT_ADMIN'] as const satisfies readonly Role[];

export function App() {
  const status = useAuthStore((state) => state.status);

  if (status === 'loading') {
    return <LoadingScreen message={fr.common.loading} />;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireRole />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />

          <Route element={<RequireRole roles={CONTENT_ROLES} />}>
            <Route path="courses" element={<CataloguePage />} />
            <Route path="courses/:courseId" element={<CourseDetailPage />} />
          </Route>

          <Route element={<RequireRole roles={STUDENT_ROLES} />}>
            <Route path="my-courses" element={<MyCoursesPage />} />
          </Route>

          <Route element={<RequireRole roles={STUDENT_ROLES} redirectOnForbidden />}>
            <Route path="my-payments" element={<MyPaymentsPage />} />
          </Route>

          <Route element={<RequireRole roles={ADMIN_ROLES} redirectOnForbidden />}>
            <Route path="payments" element={<AdminPaymentsPage />} />
          </Route>

          <Route element={<RequireRole roles={AUTHORING_ROLES} />}>
            <Route path="manage" element={<ManageCoursesPage />} />
            <Route path="manage/new" element={<CourseCreatePage />} />
            <Route path="manage/:courseId/edit" element={<CourseEditPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export function Root() {
  useSessionRestore();
  return (
    <BrandingGate>
      <App />
    </BrandingGate>
  );
}
