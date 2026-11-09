import { Route, Routes } from 'react-router-dom';

import { useSessionRestore } from './auth/useSessionRestore';
import { AppLayout } from './components/AppLayout';
import { LoadingScreen } from './components/LoadingScreen';
import { RequireRole } from './components/RequireRole';
import { fr } from './i18n/fr';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RegisterPage } from './pages/RegisterPage';
import { useAuthStore } from './store/auth';
import { BrandingGate } from './tenant/BrandingGate';

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
