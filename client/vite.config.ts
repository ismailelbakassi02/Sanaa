import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
    build: {
      rollupOptions: {
        output: {
          /**
           * Three long-lived vendor chunks plus one chunk per lazy page. Arco is
           * by far the largest dependency, so isolating it keeps the entry and
           * the page chunks small and cacheable across releases.
           */
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined;
            }
            if (id.includes('@arco-design')) {
              return 'arco';
            }
            if (id.includes('react')) {
              return 'react';
            }
            return 'vendor';
          },
        },
      },
    },
    server: {
      port: 5173,
      proxy: {
        // One domain: the client and the gateway share the same host, so the
        // browser never needs a tenant-specific hostname or query parameter.
        '/api': {
          target: apiTarget,
          changeOrigin: false,
          // Adds X-Forwarded-For/Proto/Host so the gateway rate limits by IP.
          xfwd: true,
        },
      },
    },
  };
});
