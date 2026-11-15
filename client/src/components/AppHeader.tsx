import { Avatar, Button, Dropdown, Layout, Menu, Tag } from '@arco-design/web-react';
import { IconDown, IconPoweroff } from '@arco-design/web-react/icon';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { logout } from '../api/auth';
import { BrandLogo } from './BrandLogo';
import { MainMenu } from './MainMenu';
import { ThemeToggle } from './ThemeToggle';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || 'S';
}

export function AppHeader() {
  const user = useAuthStore((state) => state.user);
  const clear = useAuthStore((state) => state.clear);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (loggingOut) {
      return;
    }
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // A revoked or expired session is already logged out locally.
    } finally {
      clear();
      queryClient.clear();
      setLoggingOut(false);
      navigate('/login', { replace: true });
    }
  };

  const userMenu = user ? (
    <Menu
      onClickMenuItem={(key) => {
        if (key === 'logout') {
          void handleLogout();
          return;
        }
        navigate(key === 'password' ? '/password' : '/me');
      }}
    >
      <Menu.Item key="me">{fr.nav.account}</Menu.Item>
      <Menu.Item key="password">{fr.nav.password}</Menu.Item>
      <Menu.Item key="logout">
        <span className="sanaa-user-menu__logout">
          <IconPoweroff aria-hidden="true" />
          {fr.nav.logout}
        </span>
      </Menu.Item>
    </Menu>
  ) : null;

  return (
    <Layout.Header className="sanaa-header">
      <div className="sanaa-header__inner">
        <div className="sanaa-header__left">
          <Link to="/" className="sanaa-header__brand" aria-label={fr.app.name}>
            <BrandLogo variant="mark" height={30} />
            <span className="sanaa-header__wordmark">{fr.app.name}</span>
          </Link>
          {user ? <MainMenu /> : null}
        </div>

        <div className="sanaa-header__actions">
          <ThemeToggle />
          {user ? (
            <Dropdown droplist={userMenu} position="br" trigger="click">
              <Button type="text" className="sanaa-header__user">
                <Avatar size={28} className="sanaa-avatar">
                  {initialOf(user.fullName)}
                </Avatar>
                <span className="sanaa-header__username">{user.fullName}</span>
                <Tag size="small" className="sanaa-header__role">
                  {fr.roles[user.role]}
                </Tag>
                <IconDown aria-hidden="true" />
              </Button>
            </Dropdown>
          ) : (
            <div className="sanaa-header__public-actions">
              <Link
                className="sanaa-header__public-link sanaa-header__public-link--creator"
                to="/become-creator"
              >
                {fr.nav.becomeCreator}
              </Link>
              <Link className="sanaa-header__public-link" to="/login">
                {fr.nav.signIn}
              </Link>
              <Link to="/register">
                <Button type="primary" size="small">
                  {fr.nav.signUp}
                </Button>
              </Link>
            </div>
          )}
        </div>
      </div>
    </Layout.Header>
  );
}
