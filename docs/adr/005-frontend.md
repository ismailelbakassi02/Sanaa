# ADR 005: Frontend stack

Status: accepted (2026-11-09)

## Context

`docs/architecture.md` section 13 plans a React single-page application using Arco Design,
TanStack Query and Zustand. AGENTS.md requires an explicit compatibility check before adopting
React 19 with Arco, and `docs/Design.md` is the UI reference. The first slice is branding, auth,
a protected shell and a French UI.

The two questions were:

1. Does `@arco-design/web-react` work with React 19, or must the client use React 18?
2. Which concrete versions and workarounds are needed so the build, lint and tests pass?

## Decision

Use React 19.3.1 with `@arco-design/web-react` 2.66.18 and import the official React 19 adapter
before the first Arco render:

```ts
import '@arco-design/web-react/es/_util/react-19-adapter';
```

No Tailwind and no second UI framework. Styling uses Arco CSS variables and a small global
stylesheet; the tenant primary color only overrides the `--primary-*` variables.

## Evidence

- Arco changelog 2.66.0 (2025-04-03): "Compatible with React 19. Please refer to the
  documentation for usage." (<https://arco.design/react/en-US/docs/changelog>)
- Arco Quick Start, section "React 19", documents the adapter import above
  (<https://arco.design/react/en-US/docs/start#react-19>). The installed package ships
  `es/_util/react-19-adapter.js`, which calls `setCreateRoot(createRoot)` on Arco's internal
  `react-dom` copy.
- Without the adapter, Arco's `Message` falls back to `ReactDOM.render`, removed in React 19
  (`CopyReactDOM.render is not a function`). With the adapter the same call works in the browser
  and in jsdom.
- A known upstream issue (#2988) reports React 19 problems in `Select`. The first slice does not
  use `Select`; it is noted as a risk for later screens, not a reason to downgrade.

The adapter only patches the ESM build (`es/`). Vitest resolves the package `main` field (`lib/`,
CommonJS) for some modules such as `Message`. The test setup therefore imports both adapters:

```ts
import '@arco-design/web-react/es/_util/react-19-adapter';
import '@arco-design/web-react/lib/_util/react-19-adapter';
```

This is a test-harness detail, not a product workaround: the application build only uses `es/`.

## Resolved versions

| Package | Version |
|---|---|
| react / react-dom | 19.3.1 |
| `@arco-design/web-react` | 2.66.18 |
| react-router-dom | 7.18.5 |
| `@tanstack/react-query` | 5.107.1 |
| zustand | 5.1.0 |
| axios | 1.21.0 |
| vite | 8.3.6 |
| `@vitejs/plugin-react` | 6.2.1 |
| typescript | 5.9.3 |
| vitest | 5.1.0 |
| jsdom | 29.1.1 |
| eslint | 9.39.5 |
| `@testing-library/react` | 16.3.3 |

jsdom 30 and 31 require Node `^24.15.0` (or Node 26); the development machine runs Node 24.13.1,
so jsdom 29.1.1 is pinned. Vite 8 requires Node 20.19 or newer, which matches the local Node 24.

## Consequences

### Arco French locale

`ConfigProvider` receives `@arco-design/web-react/es/locale/fr-FR`. In 2.66.18 the `Locale` type
requires a `ColorPicker` section that `fr-FR` does not define yet, so TypeScript rejects the
import. `src/i18n/arcoLocale.ts` merges the French locale with `enUS.ColorPicker`. The application
does not render `ColorPicker`; revisit when Arco updates `fr-FR`.

### Tenant primary color

`--primary-6` must be an `r, g, b` triplet. The tenant hex color is validated (`#rgb` or
`#rrggbb`) before use. Step 6 is darkened until white text reaches a 5.6:1 contrast ratio, because
the demo green `#00B42A` gives only about 2.4:1 with white, which fails WCAG AA. Steps 1-5 are
tints and 7-10 shades of that base, so Arco hover and pressed states stay coherent. The raw brand
hex is never used for white-text controls.

### Local development and tenancy

The Vite dev server proxies `/api` with `changeOrigin: false`, because the gateway compares the
request `Host` subdomain with the token `tenant_slug`. `xfwd: true` adds the forwarded client IP
used by the Redis rate limiter. `atlas.localhost` and `acme.localhost` are accepted through
`allowedHosts: ['.localhost']`.

### Tests

Vitest runs in jsdom with Testing Library. The refresh interceptor is tested with an injected axios
adapter and refresh function, which covers parallel 401s, refresh failure and the auth endpoint
exclusions without network access. Integration tests for the backend remain Testcontainers-based;
the frontend has no backend dependency in its unit tests.
