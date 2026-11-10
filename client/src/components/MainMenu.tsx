import { Menu } from '@arco-design/web-react';
import { useLocation, useNavigate } from 'react-router-dom';

import type { Role } from '../api/types';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useTenantSearch } from '../tenant/navigation';

export interface NavItem {
  key: string;
  label: string;
  path: string;
}

/**
 * SUPER_ADMIN manages tenants, not tenant content: course-service answers 403
 * for that role, so it gets no course entry at all and keeps the home
 * placeholder.
 */
export function navItemsForRole(role: Role): NavItem[] {
  if (role === 'SUPER_ADMIN') {
    return [];
  }
  return [
    { key: 'courses', label: fr.menu.catalogue, path: '/courses' },
    role === 'STUDENT'
      ? { key: 'my-courses', label: fr.menu.myCourses, path: '/my-courses' }
      : { key: 'manage', label: fr.menu.myFormations, path: '/manage' },
  ];
}

/** Header navigation. The tenant query string is preserved on every jump. */
export function MainMenu() {
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const location = useLocation();
  const tenantSearch = useTenantSearch();

  if (!user) {
    return null;
  }
  const items = navItemsForRole(user.role);
  if (items.length === 0) {
    return null;
  }
  const selected = items.find(
    (item) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`),
  );

  return (
    <nav className="sanaa-nav" aria-label={fr.menu.primary}>
      <Menu
        mode="horizontal"
        selectedKeys={selected ? [selected.key] : []}
        onClickMenuItem={(key) => {
          const item = items.find((candidate) => candidate.key === key);
          if (item) {
            navigate(`${item.path}${tenantSearch}`);
          }
        }}
      >
        {items.map((item) => (
          <Menu.Item key={item.key}>{item.label}</Menu.Item>
        ))}
      </Menu>
    </nav>
  );
}
