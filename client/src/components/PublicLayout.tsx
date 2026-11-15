import { Layout } from '@arco-design/web-react';
import { Outlet } from 'react-router-dom';

import { AppFooter } from './AppFooter';
import { AppHeader } from './AppHeader';

/** Header and footer for anonymous pages: home, public pages and privacy. */
export function PublicLayout() {
  return (
    <Layout className="sanaa-layout">
      <AppHeader />
      <Layout.Content className="sanaa-content">
        <Outlet />
      </Layout.Content>
      <AppFooter />
    </Layout>
  );
}
