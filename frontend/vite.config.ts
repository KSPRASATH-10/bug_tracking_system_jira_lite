import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

/**
 * The browser only ever talks to http://localhost:5173. Vite forwards /api to the Spring Boot backend, so
 * the API is same-origin from the SPA's point of view. That is what lets the SameSite=Strict, Path=/api,
 * HttpOnly session cookie work without loosening any cookie attribute.
 */
const baseSecurityHeaders: Record<string, string> = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
};

/**
 * Production-grade CSP, applied to `vite preview` (the built bundle). The dev server is excluded because
 * Vite's hot-reload client needs inline scripts. In a real deployment, set the same policy on the web
 * server or reverse proxy that serves /dist.
 *   - no inline or eval'd script, no third-party origins, no <object>/<embed>, no framing
 *   - 'unsafe-inline' is allowed for STYLES only (React style attributes); scripts stay locked down
 *   - img-src has no remote hosts, so remote images (tracking pixels) can never load
 */
const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.BTS_API_PROXY_TARGET || 'http://localhost:8080';
  const proxy = {
    '/api': {
      target: apiTarget,
      changeOrigin: false,
    },
  };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: 'localhost', // loopback only: never exposed on the LAN
      port: 5173, // fixed: the backend CORS / Origin allow-list names this exact origin
      strictPort: true,
      proxy,
      headers: baseSecurityHeaders,
    },
    preview: {
      host: 'localhost',
      port: 5173,
      strictPort: true,
      proxy,
      headers: { ...baseSecurityHeaders, 'Content-Security-Policy': contentSecurityPolicy },
    },
    build: {
      target: 'es2020',
      sourcemap: false,
      chunkSizeWarningLimit: 800,
    },
  };
});
