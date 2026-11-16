# ADR 005: Frontend stack and SANAA identity

Status: accepted (2026-11-09), revised for the v2 identity (2026-11-14) and for the
marketplace v2 pages (2026-11-16)

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
4. How does the public marketplace (catalogue, course page, reviews, learner area) stay shareable,
   accessible and cheap to load without adding a second UI framework or a state library?

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
| `/`, `/courses`, `/courses/:courseId`, `/categories/:slug`, `/publishers/:slug`, `/instructors/:id`, `/login`, `/login/google/callback`, `/login/google/link`, `/register`, `/become-creator`, `/privacy` | public |
| `/me`, `/password` | any authenticated user |
| `/learn`, `/learn/courses` | `STUDENT`, `INSTRUCTOR` or `TENANT_ADMIN` |
| `/studio/profile` | `TENANT_ADMIN` or `INSTRUCTOR`; only `TENANT_ADMIN` may save |
| `/studio/team` | `TENANT_ADMIN`; the page explains the rule for an `INDIVIDUAL` publisher |
| `/admin` | `SUPER_ADMIN` only |
| `/my-payments`, `/payments` | reachable by URL only (`STUDENT` and `TENANT_ADMIN`); no menu entry until the payment flow is wired |

`RequireRole` sends an anonymous visitor to `/login` (with the original pathname in router state)
and an authenticated user without the role to `/me`. A session with `mustChangePassword` is kept on
`/password` until the password is changed or the user logs out; the gateway and the services enforce
the same rule server-side. `/categories/:slug` is kept as a compatibility route: it redirects to
`/courses?category=<slug>`, the single search page that owns the catalogue filters.

### Hidden until the payment tasks

The v1 catalogue, course detail, my-courses and editor pages were deleted with their hooks, API
functions and tests: they called `/api/courses`, `/api/modules`, `/api/lessons` and
`/api/enrollments/me`, which the marketplace v2 replaced. Their replacements are `CoursesPage`,
`CoursePage`, `LearnCoursesPage` and the future studio editor.

The payment pages (`/my-payments`, `/payments`) stay reachable by URL but out of every menu until
the payment flow is part of the acceptance. They keep their role rules so a URL cannot be used as a
back door, and `MainMenu` only builds entries from `navItemsForRole`, so a hidden route cannot
reappear by accident.

### Menus

`navItemsForRole` is the single source of truth:

- `STUDENT`: `/`, `/courses`, `/learn`, `/me`, `/become-creator`.
- `INSTRUCTOR`: `/`, `/courses`, `/learn`, `/me`, `/studio/profile`.
- `TENANT_ADMIN`: `/`, `/courses`, `/learn`, `/me`, `/studio/profile`, plus `/studio/team` only for
  an `ORGANIZATION`.
- `SUPER_ADMIN`: `/`, `/courses`, `/me`, `/admin`. The platform administrator never learns, so it
  has no `/learn` entry.
- Anonymous visitors: `/`, `/courses`.

Desktop renders an Arco horizontal menu; below 768 px the same items open in a left `Drawer`. The
user menu holds `/me`, `/password` and logout.

## Marketplace v2

### Search page and URL contract

`/courses` is driven by the address, not by local state: `q`, `category`, `tag`, `level`, `free`,
`sort` and `page` are parsed by `lib/searchParams.ts` and written back in a stable order. A link can
therefore be shared, bookmarked and restored by the back and forward buttons. Unknown levels, sorts
or negative pages are dropped; text filters are trimmed. The API page size is 24.

The input is debounced by 300 ms and only searches from one character. A chip removes a single
filter, the reset action clears everything, and the previous result stays visible while the next
request is pending. Below 900 px the filters move into an Arco `Drawer`; the same `FilterPanel` is
used in the sidebar and in the drawer.

The URL only follows the debounced term once the debounce has caught up with the input. Without that
guard, a reset, a chip removal or a back navigation was overwritten by the previous term one render
later. A regression test covers the chip removal.

### Course card, cover and price

`CourseCard` is one link: the whole card navigates to `/courses/:id`, and the accessible name is the
title plus the publisher. The cover is generated from the title: a deterministic hash picks one Arco
colour family, step 1/3 paint the background and step 8 paints the initials. Step 6 is a fill colour
in the dark palette, so it is never used as text on the cover. There is no image request and no
hardcoded colour.

`PriceBlock` trusts the server `discountActive` flag instead of comparing `effectivePrice` with
`price`. An expired promotion therefore never shows a false strikethrough, a zero price is
"Gratuit", and the old price is hidden from assistive technology. Rating text is
"4,7 sur 5, 3 avis" or "Nouveau" when no review is visible, with the French comma from `Intl`.

### Course page and its single primary action

The description is rendered as text, never as HTML: a `<script>` in a description stays visible
text. The curriculum uses an Arco `Collapse`, and the reviews are paginated with the publisher reply
under each comment.

The sticky price card offers exactly one primary action, chosen by the pure `lib/courseAction.ts`:
login, already enrolled, own publisher, pending payment, free enrollment, buy, or unavailable. The
action comes from `GET /api/learner/courses/{id}/purchase-info`, the only trusted source for
enrollment and publisher ownership. While that request is pending or failed, no action is offered
instead of guessing from the public card. The login action carries the current path in router state
and the login page sanitizes it. The paid-course button is intentionally disabled with a French
"available soon" note until the payment task is accepted; the payment pages remain reachable only
by their guarded URLs.

### Review form

`ReviewForm` uses an Arco `Rate`, a counter capped at 2000 characters and `POST`/`PUT
/api/courses/{id}/reviews`. It prefills from `GET /api/learner/courses/{id}/my-review`, so a learner
edits their own review instead of creating a second one. A `HIDDEN` status is shown to its author
with a French explanation and the form stays editable. A successful save invalidates the course,
the reviews and the learner queries so the average and the enrolment list refresh.

### Learner area and creator pages

`/learn` shows the latest six enrollments, the teachers of the enrolled courses, suggested
categories, trending courses and every category, plus an inviting empty state. `/learn/courses`
lists every enrollment with its date and its source tag (Gratuit, Achat, Accès accordé); the watch
button is disabled with a French note until playback exists. Both routes are guarded to the three
learning roles.

The publisher and instructor pages render their real course lists with the shared card. The public
search has no publisher or instructor filter yet, so `useCatalogueWindow` reads the newest 24
published courses and filters them locally. This is an API gap, not a design choice; it is listed
below.

### Redirect safety

`GET /login` accepts a `from` location in router state so a guarded page can return the learner to
where they came from. That value is attacker-controlled, so `sanitizeRedirect` only accepts an
internal absolute path: it rejects `https://`, `//host`, `/\host`, `javascript:`, control
characters and relative paths, and falls back to the role landing page. Hostile cases are tested.

### Code splitting and chunk sizes

Every page is a `React.lazy` chunk behind one `Suspense` fallback (`RouteFallback`, French skeleton).
The layouts, guards and session restore stay eager because they wrap the first paint. `manualChunks`
isolates Arco, React and the remaining vendor code so a new release invalidates only the page chunks.

Measured with `npm run build` (Vite 8, minified, gzip in parentheses):

| Build | Entry | Arco | React | Vendor | Total JS |
|---|---|---|---|---|---|
| Before splitting (eager pages, one bundle) | 1,255.94 kB (361.94) | — | — | — | 1,255.94 kB |
| After splitting | 67.28 kB (22.82) | 954.65 kB (274.81) | 68.60 kB (22.81) | 64.44 kB (23.34) | 1,275.82 kB across 50 chunks |

The entry is far below the 500 kB budget. The Arco chunk exceeds it because it is the whole
component library and its CSS (555 kB) is imported once; isolating it keeps it immutable across
releases and out of the entry. Removing Arco would be the only way to shrink it, which the design
system forbids. The 19.88 kB increase in total minified JavaScript is the expected module/chunk
wrapper overhead; a route downloads only its page and shared chunks instead of the 1.26 MB eager
bundle. The largest page chunk is `CoursePage` at 12.95 kB; the two Google pages are 0.83 kB and
1.75 kB.

### Authentication

The access token lives in memory only (`useAuthStore`); the refresh token is an HttpOnly cookie.
A page reload calls `POST /api/auth/refresh`, then `GET /api/auth/me`. The shared axios client
rotates the refresh cookie through one shared promise when parallel requests receive 401, retries
each request once and never refreshes on `/api/auth/login`, `/register`, `/refresh` or `/logout`.
Registration does not return a token, so the register page signs the new learner in explicitly.

Google OpenID Connect is optional and additive to the normal forms. Login and registration query
`GET /api/auth/oauth2/google/status`; only an enabled backend renders "Continuer avec Google". The
button performs a top-level navigation to auth-service, while session storage keeps only a sanitized
internal return path. The callback page relies on the normal startup refresh, so no Google or SANAA
token appears in the URL. Existing-email confirmation has its own password-only page, and a
Google-created account sees "Définir un mot de passe" instead of the current-password form. The
backend protocol and linking decisions are in ADR 012.

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

### Tags, discount badge and rating stars

Arco paints a preset tag with step 6 on its step-1 background. The discount badge is a `red` tag and
the publisher type and enrollment source are `green`, `arcoblue` or `gold` tags, all at 12 px, so
normal-text AA (4.5:1) applies. Measured with the same formula, against the tag's own background:

| Tag | Light (before) | Light (used) | Dark (before) | Dark (used) |
|---|---|---|---|---|
| discount, `red` | `#F53F3F` on `#FFECE8` 3.25:1 | `--red-7` `#CB272D` 4.76:1 | `#F76965` on `#4D000A` 5.43:1 | unchanged |
| organization, `arcoblue` | `#165DFF` on `#E8F3FF` 4.62:1 | unchanged | `#3C7EFF` on `#000D4D` 4.83:1 | unchanged |
| individual / enrolled, `green` | `#00B42A` on `#E8FFEA` 2.63:1 | `--green-8` `#008026` 4.84:1 | `#27C346` on `#004D1C` 4.32:1 | `--green-7` `#50D266` 5.17:1 |
| pending / access granted, `gold` | `#F7BA1E` on `#FFFCE8` 1.70:1 | `--orange-8` `#A64500` 5.86:1 | `#F9CC44` on `#4D2D00` 8.13:1 | `--gold-8` `#FCE995` 10.19:1 |
| rating star (icon) | `#F7BA1E` on white 1.75:1 | `--gold-8` `#A26D0A` 4.44:1 | `#F9CC44` on `#232324` 10.28:1 | unchanged |

`styles/global.css` applies these overrides once, so every preset tag keeps Arco's shape and layout
and only its text step changes. The star is decorative (the rating is written next to it), but the
icon still reaches 3:1 and close to 4.5:1 in light mode.

The final Lighthouse pass also found Arco's `--color-text-3` at 3.24:1 on white and `--green-6`
at 2.77:1 for the free price. Captions, muted labels, creator names, levels and footer links now use
`--color-text-2` (7.10:1); the free price uses `--green-8` (5.10:1). Lighthouse reports 1.00 for
accessibility, best practices and SEO on both the home page and a reviewed course page.

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
- A known upstream issue (#2988) reports React 19 problems in `Select`. The marketplace search uses
  `Select` for the category, level and sort filters; the local build, the jsdom suite and the
  acceptance run exercise it. The adapter remains required, and a regression there is an Arco
  upgrade concern, not a reason to downgrade React.

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
- `GET /api/public/courses` has no publisher or instructor filter. The publisher and instructor
  pages read the newest 24 published courses and filter them locally. A creator with more than 24
  published courses would hide older ones; the proper fix is a `publisherSlug` / `instructorId`
  parameter on the public search.
- In the local acceptance stack, `GET /api/payments/me` returns 401 for a valid `STUDENT` token
  (confirmed independently through the gateway). The marketplace therefore cannot verify an open
  payment or the live paid-course flow. The paid CTA stays disabled and explains that online buying
  is coming later; free enrollment and reviews remain fully functional.
- The API omits null fields (`default-property-inclusion: non_null`). Types mark optional data as
  `T | null`, and the UI treats a missing field as null instead of assuming it is present.
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
byte rule, URL safety (`javascript:`, `http:`, embedded credentials, `//host`, `/\host`, control
characters), the contrast helper and brand measurements, the brand logo (inline SVG, no network),
the menus and guards for every role, the forced password redirect, become-creator validations and
both 409 conflicts, the shared refresh interceptor, a bio containing a `<script>` rendered as text,
a `javascript:` link hidden on public pages, the learner-membership conflict message and the
individual-publisher team message.

The marketplace v2 adds tests for the price block (free, paid, active and expired discount, the
French `Intl` format), the rating text, the deterministic cover colour, search parameter parsing and
serialising, the 300 ms debounce, chip removal, the redirect sanitizer, the course action selection
for every state, review validation and the hidden-review message, the learner empty state, the route
guards, a `<script>` description rendered as text, and the lazy routes behind Suspense. The pages are
rendered through the real router so `useParams` is exercised. Integration tests for the backend
remain Testcontainers-based; the frontend has no backend dependency in its unit tests.

Google authentication tests keep the normal forms visible, conditionally render the Google button,
reject a hostile remembered route, consume the return route once, cover callback recovery and
existing-account password confirmation, and verify that a Google-created account can define its
first normal password. Provider protocol validation remains an auth-service responsibility.

### Visual acceptance (2026-11-16)

`docs/screenshots/` holds 40 current captures at 1440x900 and 390x844, in light and dark mode. They
cover the home page, the full catalogue, the accent-insensitive "etalonnage" search, a reviewed
course with a publisher reply, a discounted course, a paid course with its disabled CTA, the Atlas
publisher, Omar's instructor profile, `/learn` and `/learn/courses`. The old 62 captures were
deleted before the final retake.

The captures come from `scripts/dev-start.ps1 -Seed` and Edge driven by Playwright. Each capture
asserts exactly one `h1`; all API responses are checked, with only the documented anonymous refresh,
missing-my-review and payment-service responses treated as expected. No page exception or other 4xx
or 5xx response occurred.

The first pass identified the three weakest visual points, fixed them, then retook every image:

1. Anonymous users had no public navigation even though `MainMenu` defined Accueil and Cours. The
   header now always renders the menu: both entries are visible on desktop and an accessible drawer
   button appears at 390 px.
2. A fixed four-column course grid left very narrow cards beside the catalogue filters and inside
   the narrow instructor profile. Those grids now use `auto-fit` with a 220 px minimum: three
   readable cards in the desktop catalogue and instructor page, one on a phone.
3. On a phone, the single enrollment action came after the description, curriculum and reviews.
   `CoursePage` now chooses one responsive placement: the same price card appears immediately after
   the course summary below 1200 px and remains a sticky right-hand card on desktop. It is never
   duplicated in the DOM.

The final pass also separates the footer links and keeps the public/authenticated menus active on
the correct route. The expected console noise is limited to the anonymous `POST /api/auth/refresh`
probe (401), `GET .../my-review` (404 means no review yet), and the payment API gap above.
