import '@arco-design/web-react/es/_util/react-19-adapter';
import '@arco-design/web-react/dist/css/arco.css';
import './styles/global.css';

import { ConfigProvider } from '@arco-design/web-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import { Root } from './App';
import { arcoLocale } from './i18n/arcoLocale';
import { applyTheme, useThemeStore } from './store/theme';

// applyTheme also derives the brand scale, so both the Arco dark attribute and
// the primary ramp are set before the first paint.
applyTheme(useThemeStore.getState().mode);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element is missing');
}

createRoot(container).render(
  <StrictMode>
    <ConfigProvider locale={arcoLocale}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Root />
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  </StrictMode>,
);
