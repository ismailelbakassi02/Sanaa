import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // Tenant hosts are served by the same dev server: atlas.localhost,
      // acme.localhost and so on. Vite must accept these hostnames.
      allowedHosts: ['.localhost'],
      proxy: {
        '/api': {
          target: apiTarget,
          // changeOrigin must stay false: the gateway compares the Host
          // subdomain with the tenant_slug claim of the access token.
          changeOrigin: false,
          // Adds X-Forwarded-For/Proto/Host so the gateway rate limits by IP.
          xfwd: true,
        },
      },
    },
  };
});
