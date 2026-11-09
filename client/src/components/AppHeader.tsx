import { Avatar, Button, Dropdown, Layout, Menu } from '@arco-design/web-react';
import { IconDown, IconPoweroff } from '@arco-design/web-react/icon';
import { Link, useNavigate } from 'react-router-dom';

import { logout } from '../api/auth';
import { BrandMark } from './BrandMark';
import { ThemeToggle } from './ThemeToggle';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useBranding } from '../tenant/BrandingContext';
import { useTenantSearch } from '../tenant/navigation';

function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || 'S';
}

export function AppHeader() {
  const { branding } = useBranding();
  const user = useAuthStore((state) => state.user);
  const clear = useAuthStore((state) => state.clear);
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();

  if (!user) {
    return null;
  }

  const handleMenuClick = (key: string) => {
    if (key !== 'logout') {
      return;
    }
    void (async () => {
      try {
        // The local session is cleared even if the API call fails.
        await logout();
      } catch {
        // Nothing else to do: the refresh cookie may already be gone.
      } finally {
        clear();
        navigate(`/login${tenantSearch}`, { replace: true });
      }
    })();
  };

  return (
    <Layout.Header className="sanaa-header">
      <div className="sanaa-header__inner">
        <Link to={`/${tenantSearch}`} className="sanaa-brand sanaa-brand--link">
          <BrandMark name={branding.name} logoUrl={branding.logoUrl} />
          <span className="sanaa-brand__name sanaa-header__tenant">{branding.name}</span>
        </Link>

        <div className="sanaa-header__actions">
          <ThemeToggle />
          <Dropdown
            trigger="click"
            position="br"
            droplist={
              <Menu selectable={false} onClickMenuItem={handleMenuClick}>
                <Menu.Item key="account" disabled>
                  <div className="sanaa-menu-account">
                    <span className="sanaa-menu-account__name">{user.fullName}</span>
                    <span className="sanaa-menu-account__meta">{fr.roles[user.role]}</span>
                    <span className="sanaa-menu-account__meta">{user.email}</span>
                  </div>
                </Menu.Item>
                <Menu.Item key="logout">
                  <IconPoweroff />
                  <span className="sanaa-menu-account__action">{fr.menu.logout}</span>
                </Menu.Item>
              </Menu>
            }
          >
            <Button type="text" className="sanaa-header__user" aria-label={fr.menu.userMenu}>
              <Avatar size={28} className="sanaa-avatar">
                {initialOf(user.fullName)}
              </Avatar>
              <span className="sanaa-header__username">{user.fullName}</span>
              <IconDown />
            </Button>
          </Dropdown>
        </div>
      </div>
    </Layout.Header>
  );
}
