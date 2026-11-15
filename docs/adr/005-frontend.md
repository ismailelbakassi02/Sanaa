# ADR 005: Frontend stack and SANAA identity

Status: accepted (2026-11-09), revised for the v2 identity (2026-11-14)

## Context

`docs/architecture.md` section 13 plans a React single-page application using Arco Design,
TanStack Query and Zustand. AGENTS.md requires an explicit compatibility check before adopting
React 19 with Arco, and `docs/Design.md` is the UI reference.

The v1 client detected a tenant from the hostname or `?tenant=`, asked the visitor to pick an
organisation and sent `tenantSlug` to the gateway. The v2 product has one brand, SANAA, and one
public domain: a publisher is selected by a slug in the public URL (`/publishers/:slug`), never by
a subdomain. The client therefore has no tenant detection, no organisation picker and no
tenant-specific theme.

The open questions were:

1. Does `@arco-design/web-react` work with React 19, or must the client use React 18?
2. How does one brand color produce a complete, accessible Arco primary scale in light and dark
   mode?
3. Which routes, menus and guards does the v2 identity need for the four platform roles?

## Decision

### React and Arco

Use React 19.3.1 with `@arco-design/web-react` 2.66.18 and import the official React 19 adapter
before the first Arco render:

```ts
import '@arco-design/web-react/es/_util/react-19-adapter';
```

No Tailwind and no second UI framework. Styling uses Arco CSS variables plus a small global
stylesheet, `client/src/styles/global.css`.

### One brand, one domain

- The single brand color is `#195A56`, exported once as `BRAND_COLOR` from
  `client/src/lib/brand.ts`.
- `applyBrandTheme(mode)` writes `--sanaa-brand-rgb` (`25, 90, 86`) and derives the ten Arco
  `--primary-1..10` triplets from that one constant. It runs before the first paint and again when
  the theme changes.
- Components never hardcode a hex color. They use `rgb(var(--primary-6))`,
  `rgb(var(--sanaa-brand-rgb))` or an Arco token.
- The public publisher page scopes its own `--primary-*` and `--sanaa-brand-rgb` values on a
  wrapper, so a creator's accent colors that page only and never leaks into the platform shell.
- The client no longer reads `VITE_BASE_DOMAIN`, `VITE_DEFAULT_TENANT` or `?tenant=`. The only
  frontend environment variable is `VITE_API_TARGET`. Vite still proxies `/api` with
  `changeOrigin: false` and `xfwd: true`; the gateway no longer compares `Host` with a tenant, but
  the forwarded client IP is still used by the Redis rate limiter.

### Logo pipeline

The source files live in `docs/brand/` (`sanaa-logo.svg`, `sanaa-mark.svg`) with the exported PNGs
used by `client/public/brand/` (favicon, `icon-180`, `icon-192`, `icon-512`). `BrandLogo` embeds the
path data from `client/src/components/brandPaths.ts` as inline SVG, so the logo paints before any
network request, inherits `currentColor` in light and dark mode, and always exposes the accessible
name "SANAA". The favicon, apple-touch-icon, web manifest (`#195A56` theme color) and `theme-color`
meta tag complete the identity without a registered-trademark symbol.

### Routes

| Route | Access |
|---|---|
| `/`, `/login`, `/register`, `/become-creator`, `/publishers/:slug`, `/instructors/:id`, `/privacy` | public |
| `/me`, `/password` | any authenticated user |
| `/studio/profile` | `TENANT_ADMIN` or `INSTRUCTOR`; only `TENANT_ADMIN` may save |
| `/studio/team` | `TENANT_ADMIN`; the page explains the rule for an `INDIVIDUAL` publisher |
| `/admin` | `SUPER_ADMIN` only |

`RequireRole` sends an anonymous visitor to `/login` (with the original pathname in router state)
and an authenticated user without the role to `/me`. A session with `mustChangePassword` is kept on
`/password` until the password is changed or the user logs out; the gateway and the services enforce
the same rule server-side.

### Hidden until the marketplace tasks

The course and payment pages are kept and still compile, but no menu links to them and they are not
part of the v2 acceptance: `/courses`, `/courses/:courseId`, `/my-courses`, `/my-payments`,
`/payments`, `/manage`, `/manage/new`, `/manage/:courseId/edit`. They keep their v1 role rules so a
URL cannot be used as a back door while they wait for the marketplace work. `MainMenu` only builds
entries from `navItemsForRole`, so a hidden route cannot reappear by accident.

### Menus

`navItemsForRole` is the single source of truth:

- `STUDENT`: `/`, `/me`, `/become-creator`.
- `INSTRUCTOR`: `/`, `/me`, `/studio/profile`.
- `TENANT_ADMIN`: `/`, `/me`, `/studio/profile`, plus `/studio/team` only for an `ORGANIZATION`.
- `SUPER_ADMIN`: `/`, `/me`, `/admin`.

Desktop renders an Arco horizontal menu; below 768 px the same items open in a left `Drawer`. The
user menu holds `/me`, `/password` and logout.

### Authentication

The access token lives in memory only (`useAuthStore`); the refresh token is an HttpOnly cookie.
A page reload calls `POST /api/auth/refresh`, then `GET /api/auth/me`. The shared axios client
rotates the refresh cookie through one shared promise when parallel requests receive 401, retries
each request once and never refreshes on `/api/auth/login`, `/register`, `/refresh` or `/logout`.
Registration does not return a token, so the register page signs the new learner in explicitly.

## Contrast measurements

Arco primary buttons use white text on step 6, so step 6 must reach WCAG AA for normal text
(4.5:1). Links and focus rings use step 8 in dark mode. Measured with the WCAG 2.1 formula in
`client/src/lib/color.ts`:

| Foreground | Background | Ratio | Use |
|---|---|---|---|
| `#FFFFFF` | `#195A56` (light step 6) | 7.96:1 | primary button text |
| `#FFFFFF` | `#3C736F` (light step 5 hover) | 5.42:1 | hovered primary button |
| `#6A9491` (dark step 8) | `#17171A` (dark page) | 5.33:1 | dark links and focus |
| `#276460` (dark step 6) | `#17171A` (dark page) | 2.62:1 | dark filled button, not text |
| `#195A56` | `#17171A` (dark page) | 2.25:1 | never used as dark text |

The light scale darkens the brand only when white text would miss 4.5:1; `#195A56` already passes,
so step 6 is the exact brand color. The dark scale lightens step 6 by 6 % so the filled button
separates from the dark page, makes step 5 the lighter hover and step 8 the readable link step.
`client/src/lib/color.test.ts` locks these measurements.

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
- A known upstream issue (#2988) reports React 19 problems in `Select`. The v2 identity does not
  use `Select`; it is noted as a risk for the marketplace screens, not a reason to downgrade.

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

### API gaps carried by the UI

- `GET /api/auth/publishers/me` does not exist. The studio profile preloads the public
  `GET /api/public/publishers/{slug}` response instead, which has no contact e-mail. The page
  therefore requires the contact e-mail again on save and shows a French notice explaining why;
  saving without it would clear the stored value.
- `UpdatePublisherRequest` has no `logoImageId`. A logo is set immediately by
  `POST /api/auth/images?kind=logo`, and the page refreshes the public query after the upload.
- These are backend gaps, not client choices. They are listed in the task report and should be
  removed when the missing fields or endpoint exist.

### Role behaviour that needed a product decision

- An `INSTRUCTOR` may open `/studio/profile` read-only. Only a `TENANT_ADMIN` can save it.
- `/studio/team` answers with an explicit French message for an `INDIVIDUAL` publisher instead of a
  blank table. The API also rejects team management for that type, so the page never calls it.
- The platform administrator cannot create a publisher: `/become-creator` explains that a separate
  learner account is required. A learner who already owns a publisher is sent to the existing
  studio.

### Tests

Vitest runs in jsdom with Testing Library. The suite covers the slug rules, the UTF-8 password
byte rule, URL safety (`javascript:`, `http:`, embedded credentials), the contrast helper and brand
measurements, the brand logo (inline SVG, no network), the menus and guards for every role, the
forced password redirect, become-creator validations and both 409 conflicts, the shared refresh
interceptor, a bio containing a `<script>` rendered as text, a `javascript:` link hidden on public
pages, the learner-membership conflict message and the individual-publisher team message.
Integration tests for the backend remain Testcontainers-based; the frontend has no backend
dependency in its unit tests.

### Visual acceptance (2026-11-15)

`docs/screenshots/` holds 62 captures of the v2 pages at 1440x900 and 390x844, in light and dark
mode, taken from the local stack (no seed script) with the browser console recorded. The folder is
ignored by Git: the captures stay local and are never committed. The pre-v2 captures of the hidden
course and payment routes were removed: they showed the tenant header and tenant menus that no
longer exist.

The review found and fixed six issues:

- No navigation entry ever showed as active: `selectedKeys` received the role keys (`studio-team`)
  while Arco item keys are the route paths (`/studio/team`). MainMenu now passes the matched path
  and a test asserts the selected item.
- The become-creator stepper was off by one (`current={step}`): the first step looked inactive and
  the second one highlighted the first. It now passes `current={step + 1}`.
- Password fields used only a custom validator, so they had no required marker even though an empty
  value was rejected. The four forms now also carry `required: true`.
- At 390 px the anonymous header hid "Connexion" together with "Devenir créateur", leaving a
  returning user no way to sign in from the home page. Only the creator link is hidden now.
- The studio profile form stretched across the full 1132 px card; it is capped at 760 px.
- The individual-publisher team page showed the organization subtitle ("add instructors to your
  organization") above the message saying an individual creator cannot manage a team.

The expected console noise is limited to the anonymous `POST /api/auth/refresh` probe (401) and an
Arco `element.ref` warning under React 19 on the administration table; no page errors and no
unexpected redirects were observed.

