import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingScreen } from './LoadingScreen';
import type { Role } from '../api/types';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

interface RequireRoleProps {
  /** Omit to require any authenticated user. */
  roles?: readonly Role[];
  /** Where a signed-in user without the role is sent. Defaults to /me. */
  redirectTo?: string;
  children?: ReactNode;
}

/**
 * Route guard. Anonymous visitors go to the login page, authenticated users
 * without the required role are redirected instead of seeing a dead end, and
 * a forced password change keeps the session on /password.
 */
export function RequireRole({ roles, redirectTo = '/me', children }: RequireRoleProps) {
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingScreen message={fr.common.loading} />;
  }

  if (status !== 'authenticated' || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (user.mustChangePassword && location.pathname !== '/password') {
    return <Navigate to="/password" replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to={redirectTo} replace />;
  }

  return children ?? <Outlet />;
}
