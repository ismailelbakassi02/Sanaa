import { Button, Result } from '@arco-design/web-react';
import type { ReactNode } from 'react';
import { Navigate, Outlet, useNavigate } from 'react-router-dom';

import { LoadingScreen } from './LoadingScreen';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import type { Role } from '../api/types';
import { useTenantSearch } from '../tenant/navigation';

interface RequireRoleProps {
  /** Omit to require any authenticated user. */
  roles?: readonly Role[];
  /**
   * Payment routes are not advertised to the other roles, so a direct URL
   * sends the visitor home instead of showing the generic forbidden page.
   */
  redirectOnForbidden?: boolean;
  children?: ReactNode;
}

export function RequireRole({ roles, redirectOnForbidden, children }: RequireRoleProps) {
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();

  if (status === 'loading') {
    return <LoadingScreen message={fr.common.loading} />;
  }
  if (!user) {
    return <Navigate to={`/login${tenantSearch}`} replace />;
  }
  if (roles && !roles.includes(user.role)) {
    if (redirectOnForbidden) {
      return <Navigate to={`/${tenantSearch}`} replace />;
    }
    return (
      <div className="sanaa-center-page">
        <Result
          status="403"
          title={fr.forbidden.title}
          subTitle={fr.forbidden.body}
          extra={
            <Button type="primary" onClick={() => navigate(`/${tenantSearch}`)}>
              {fr.forbidden.backHome}
            </Button>
          }
        />
      </div>
    );
  }

  return children ?? <Outlet />;
}
