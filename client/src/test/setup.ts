import '@testing-library/jest-dom/vitest';
// Arco ships the ESM (es/) and CJS (lib/) builds with separate React 19
// adapters. Vite uses es/ for the app, but Vitest can resolve the CJS build for
// some modules such as Message, so both entry points are patched here.
import '@arco-design/web-react/es/_util/react-19-adapter';
import '@arco-design/web-react/lib/_util/react-19-adapter';

import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

vi.stubGlobal(
  'ResizeObserver',
  class ResizeObserverStub {
    observe(): void {}

    unobserve(): void {}

    disconnect(): void {}
  },
);

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList,
});

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});
