import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingScreen } from './LoadingScreen';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

/**
 * A session created with a temporary password may only change the password or
 * log out. This gate protects client routes; auth-service and the gateway
 * enforce the same rule server-side.
 */
export function ForcedPasswordGate({ children }: { children?: ReactNode }) {
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingScreen message={fr.common.loading} />;
  }

  if (
    status === 'authenticated' &&
    user?.mustChangePassword &&
    location.pathname !== '/password'
  ) {
    return <Navigate to="/password" replace />;
  }

  return children ?? <Outlet />;
}
