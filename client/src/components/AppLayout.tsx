import { Layout } from '@arco-design/web-react';
import { Outlet } from 'react-router-dom';

import { AppHeader } from '../components/AppHeader';

export function AppLayout() {
  return (
    <Layout className="sanaa-layout">
      <AppHeader />
      <Layout.Content className="sanaa-content">
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
