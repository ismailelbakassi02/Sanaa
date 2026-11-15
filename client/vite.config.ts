import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
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
