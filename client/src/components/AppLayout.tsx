import { Layout } from '@arco-design/web-react';
import { Outlet } from 'react-router-dom';

import { AppFooter } from './AppFooter';
import { AppHeader } from './AppHeader';

export function AppLayout() {
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
