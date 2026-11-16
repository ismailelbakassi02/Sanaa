import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';

import type { Role } from './api/types';
import { useSessionRestore } from './auth/useSessionRestore';
import { AppLayout } from './components/AppLayout';
import { ForcedPasswordGate } from './components/ForcedPasswordGate';
import { LoadingScreen } from './components/LoadingScreen';
import { PublicLayout } from './components/PublicLayout';
import { RequireRole } from './components/RequireRole';
import { RouteFallback } from './components/RouteFallback';
import { fr } from './i18n/fr';
import { useAuthStore } from './store/auth';

/**
 * Every page is a separate chunk: a visitor reading the home page never
 * downloads the studio, payment or admin code. The layouts and guards stay
 * eager because they wrap the first paint.
 */
const HomePage = lazy(() => import('./pages/HomePage').then((m) => ({ default: m.HomePage })));
const CoursesPage = lazy(() =>
  import('./pages/CoursesPage').then((m) => ({ default: m.CoursesPage })),
);
const CoursePage = lazy(() =>
  import('./pages/CoursePage').then((m) => ({ default: m.CoursePage })),
);
const CategoryPage = lazy(() =>
  import('./pages/CategoryPage').then((m) => ({ default: m.CategoryPage })),
);
const PublicPublisherPage = lazy(() =>
  import('./pages/PublicPublisherPage').then((m) => ({ default: m.PublicPublisherPage })),
);
const PublicInstructorPage = lazy(() =>
  import('./pages/PublicInstructorPage').then((m) => ({ default: m.PublicInstructorPage })),
);
const BecomeCreatorPage = lazy(() =>
  import('./pages/BecomeCreatorPage').then((m) => ({ default: m.BecomeCreatorPage })),
);
const PrivacyPage = lazy(() =>
  import('./pages/PrivacyPage').then((m) => ({ default: m.PrivacyPage })),
);
const LoginPage = lazy(() =>
  import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })),
);
const RegisterPage = lazy(() =>
  import('./pages/RegisterPage').then((m) => ({ default: m.RegisterPage })),
);
const NotFoundPage = lazy(() =>
  import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
);
const MePage = lazy(() => import('./pages/MePage').then((m) => ({ default: m.MePage })));
const PasswordPage = lazy(() =>
  import('./pages/PasswordPage').then((m) => ({ default: m.PasswordPage })),
);
const LearnPage = lazy(() =>
  import('./pages/LearnPage').then((m) => ({ default: m.LearnPage })),
);
const LearnCoursesPage = lazy(() =>
  import('./pages/LearnCoursesPage').then((m) => ({ default: m.LearnCoursesPage })),
);
const StudioProfilePage = lazy(() =>
  import('./pages/StudioProfilePage').then((m) => ({ default: m.StudioProfilePage })),
);
const StudioTeamPage = lazy(() =>
  import('./pages/StudioTeamPage').then((m) => ({ default: m.StudioTeamPage })),
);
const AdminPage = lazy(() =>
  import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })),
);
const AdminPaymentsPage = lazy(() =>
  import('./pages/AdminPaymentsPage').then((m) => ({ default: m.AdminPaymentsPage })),
);
const MyPaymentsPage = lazy(() =>
  import('./pages/MyPaymentsPage').then((m) => ({ default: m.MyPaymentsPage })),
);

/**
 * Payment pages stay reachable by URL but out of every menu until the payment
 * task. The platform administrator cannot use the learner API at all.
 */
const LEARNER_ROLES = ['STUDENT', 'INSTRUCTOR', 'TENANT_ADMIN'] as const satisfies readonly Role[];
const STUDENT_ROLES = ['STUDENT'] as const satisfies readonly Role[];
const STUDIO_ROLES = ['TENANT_ADMIN', 'INSTRUCTOR'] as const satisfies readonly Role[];
const ADMIN_ROLES = ['TENANT_ADMIN'] as const satisfies readonly Role[];
const PLATFORM_ADMIN_ROLES = ['SUPER_ADMIN'] as const satisfies readonly Role[];

export function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="courses" element={<CoursesPage />} />
        <Route path="courses/:courseId" element={<CoursePage />} />
        <Route path="categories/:slug" element={<CategoryPage />} />
        <Route path="publishers/:slug" element={<PublicPublisherPage />} />
        <Route path="instructors/:id" element={<PublicInstructorPage />} />
        <Route path="become-creator" element={<BecomeCreatorPage />} />
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
          path="learn"
          element={
            <RequireRole roles={LEARNER_ROLES}>
              <LearnPage />
            </RequireRole>
          }
        />
        <Route
          path="learn/courses"
          element={
            <RequireRole roles={LEARNER_ROLES}>
              <LearnCoursesPage />
            </RequireRole>
          }
        />
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
      </Route>
      </Routes>
    </Suspense>
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
