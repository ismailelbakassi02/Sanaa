import { Button, Drawer, Menu, Tooltip } from '@arco-design/web-react';
import { IconMenu } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import type { MeResponse, PublisherType, Role } from '../api/types';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

export interface NavItem {
  key: string;
  label: string;
  path: string;
}

/**
 * The role menu. The public catalogue and the learner area are open to every
 * role that can enroll; the platform administrator never learns, so it has no
 * "Mes cours" entry. The team entry exists only for an ORGANIZATION
 * administrator. Payment pages stay out of every menu.
 */
export function navItemsForRole(role: Role, tenantType?: PublisherType | null): NavItem[] {
  switch (role) {
    case 'STUDENT':
      return [
        { key: 'home', label: fr.nav.home, path: '/' },
        { key: 'courses', label: fr.nav.catalogue, path: '/courses' },
        { key: 'learn', label: fr.nav.learn, path: '/learn' },
        { key: 'me', label: fr.nav.me, path: '/me' },
        { key: 'become-creator', label: fr.nav.becomeCreator, path: '/become-creator' },
      ];
    case 'INSTRUCTOR':
      return [
        { key: 'home', label: fr.nav.home, path: '/' },
        { key: 'courses', label: fr.nav.catalogue, path: '/courses' },
        { key: 'learn', label: fr.nav.learn, path: '/learn' },
        { key: 'me', label: fr.nav.me, path: '/me' },
        { key: 'studio-profile', label: fr.nav.studioProfile, path: '/studio/profile' },
      ];
    case 'TENANT_ADMIN':
      return [
        { key: 'home', label: fr.nav.home, path: '/' },
        { key: 'courses', label: fr.nav.catalogue, path: '/courses' },
        { key: 'learn', label: fr.nav.learn, path: '/learn' },
        { key: 'me', label: fr.nav.me, path: '/me' },
        { key: 'studio-profile', label: fr.nav.studioProfile, path: '/studio/profile' },
        ...(tenantType === 'ORGANIZATION'
          ? [{ key: 'studio-team', label: fr.nav.studioTeam, path: '/studio/team' }]
          : []),
      ];
    case 'SUPER_ADMIN':
      return [
        { key: 'home', label: fr.nav.home, path: '/' },
        { key: 'courses', label: fr.nav.catalogue, path: '/courses' },
        { key: 'me', label: fr.nav.me, path: '/me' },
        { key: 'admin', label: fr.nav.admin, path: '/admin' },
      ];
    default:
      return [];
  }
}

/** Anonymous visitors get the public catalogue entries. */
export function navItemsForUser(user: MeResponse | null): NavItem[] {
  if (user) {
    return navItemsForRole(user.role, user.tenantType);
  }
  return [
    { key: 'home', label: fr.nav.home, path: '/' },
    { key: 'courses', label: fr.nav.catalogue, path: '/courses' },
  ];
}

/**
 * Arco matches selectedKeys against the menu item keys, and MainMenu uses the
 * item path as the key (the click handler navigates with it). Returning the
 * path, not the role key, is what makes the active entry highlight.
 */
function selectedPath(items: NavItem[], pathname: string): string | undefined {
  const match = [...items]
    .sort((first, second) => second.path.length - first.path.length)
    .find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`));
  return match?.path;
}

interface MainMenuProps {
  items?: NavItem[];
}

/** Desktop horizontal menu and phone Drawer, both driven by navItemsForRole. */
export function MainMenu({ items: providedItems }: MainMenuProps) {
  const user = useAuthStore((state) => state.user);
  const items = providedItems ?? navItemsForUser(user);
  const isMobile = useMediaQuery('(max-width: 767px)');
  const [open, setOpen] = useState(false);
  const pathname = useLocation().pathname;
  const navigate = useNavigate();

  if (items.length === 0) {
    return null;
  }

  const selected = selectedPath(items, pathname);
  const select = (path: string) => {
    setOpen(false);
    navigate(path);
  };

  if (isMobile) {
    return (
      <div className="sanaa-nav sanaa-nav--mobile">
        <Tooltip content={fr.nav.openMenu}>
          <Button
            type="text"
            shape="circle"
            aria-label={fr.nav.openMenu}
            icon={<IconMenu />}
            onClick={() => setOpen(true)}
          />
        </Tooltip>
        <Drawer
          visible={open}
          title={fr.nav.label}
          placement="left"
          width={280}
          footer={null}
          onCancel={() => setOpen(false)}
        >
          <Menu mode="vertical" selectedKeys={selected ? [selected] : []} onClickMenuItem={select}>
            {items.map((item) => (
              <Menu.Item key={item.path}>
                <Link to={item.path} onClick={() => setOpen(false)}>
                  {item.label}
                </Link>
              </Menu.Item>
            ))}
          </Menu>
        </Drawer>
      </div>
    );
  }

  return (
    <nav className="sanaa-nav" aria-label={fr.nav.label}>
      <Menu
        mode="horizontal"
        ellipsis={false}
        selectedKeys={selected ? [selected] : []}
        onClickMenuItem={(key) => navigate(key)}
      >
        {items.map((item) => (
          <Menu.Item key={item.path}>
            <Link to={item.path}>{item.label}</Link>
          </Menu.Item>
        ))}
      </Menu>
    </nav>
  );
}
