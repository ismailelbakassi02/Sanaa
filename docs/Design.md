# UI design reference: Arco Design

Source repository: https://github.com/arco-design/arco-design
Official site: https://arco.design
Package: `@arco-design/web-react` (React) and `@arco-design/web-vue` (Vue)
License: MIT

This document describes the visual language, tokens, component choice and code conventions used with Arco Design. Where this document and the official docs disagree, the official docs at https://arco.design win. Token values below are the library defaults; a project may override them through a custom theme.

---

## 1. What Arco Design Is

Arco Design is an enterprise-grade design system created by ByteDance. It ships:

- A React component library with 60+ production-ready components, written in TypeScript.
- A Vue 3 component library built on the same design language.
- A large set of design tokens that can be overridden to create custom themes.
- Built-in dark mode.
- An icon library (Icon Box), a theme platform (Design Lab), a material market, a Figma component library, and an application scaffold (Arco Pro).

Design character: clean, neutral, data-dense, and professional. It is built for admin consoles, dashboards, forms, tables, and internal tools. The look is flat with light borders, a restrained blue primary color, small border radii, and generous use of neutral gray surfaces. It is not playful, glassy, or heavily decorative.

---

## 2. Core Rules

Follow these rules whenever building UI in the Arco style.

1. Use Arco components and Arco tokens before writing custom CSS. Do not rebuild a Button, Select, Table, Modal, or Form from scratch.
2. Never hardcode colors that already exist as tokens. Use CSS variables such as `var(--color-text-1)` or `rgb(var(--primary-6))`.
3. Use the neutral gray scale for structure (backgrounds, borders, text) and reserve color for meaning (primary action, status, data).
4. One primary action per region. A page or dialog should have at most one filled primary button; other actions use secondary, outline, or text buttons.
5. Keep border radius small. The default is 2px for most controls. Cards and modals use 4px to 8px. Avoid large pill shapes except for Tags, Badges, and explicitly round Buttons.
6. Keep shadows subtle and use them only for floating layers (dropdowns, popovers, modals, drawers, message toasts). Static cards rely on borders or a light background fill, not shadows.
7. Respect the 4px spacing grid. Padding, margin, and gap values should be multiples of 4 (4, 8, 12, 16, 20, 24, 32, 40, 48).
8. Control heights follow the size scale: mini 24px, small 28px, default 32px, large 36px. Mixed heights on one row are a bug.
9. Text uses the four-level text color scale. Do not use pure black for body text.
10. Always support dark mode by using tokens; never use fixed hex values for surfaces or text in components that should theme.
11. Use semantic status colors consistently: success is green, warning is orange, danger is red, information and primary are blue.
12. Provide empty, loading, and error states for every data view (Empty, Spin, Skeleton, Result, Alert).

---

## 3. Installation and Setup (React)

```bash
npm install @arco-design/web-react
# or
yarn add @arco-design/web-react
```

Minimal usage:

```tsx
import React from 'react';
import { createRoot } from 'react-dom/client';
import { Button } from '@arco-design/web-react';
import '@arco-design/web-react/dist/css/arco.css';

function App() {
  return <Button type="primary">Hello Arco</Button>;
}

createRoot(document.getElementById('app')!).render(<App />);
```

Notes:

- Import `@arco-design/web-react/dist/css/arco.css` once at the app entry to load all component styles.
- For on-demand style loading and theming, use the bundler plugins from https://github.com/arco-design/arco-plugins (webpack, Vite, and others).
- Icons come from `@arco-design/web-react/icon`, for example `import { IconSearch } from '@arco-design/web-react/icon';`.
- Global configuration (locale, size, prefix, component defaults) is done with `ConfigProvider`.

```tsx
import { ConfigProvider } from '@arco-design/web-react';
import enUS from '@arco-design/web-react/es/locale/en-US';

<ConfigProvider locale={enUS} componentConfig={{ Button: { size: 'default' } }}>
  <App />
</ConfigProvider>
```

---

## 4. Design Tokens

### 4.1 Color palette

Arco builds every color from a 10-step scale (step 1 is the lightest tint, step 10 the darkest shade, step 6 is the base color). CSS variables are exposed as RGB triplets, so use them as `rgb(var(--arcoblue-6))`.

**Primary: arcoblue**

| Step | Hex |
|---|---|
| 1 | #E8F3FF |
| 2 | #BEDAFF |
| 3 | #94BFFF |
| 4 | #6AA1FF |
| 5 | #4080FF |
| 6 | #165DFF (base primary) |
| 7 | #0E42D2 |
| 8 | #072CA6 |
| 9 | #031A79 |
| 10 | #000D4D |

**Functional colors (base step 6)**

| Role | Color name | Base hex |
|---|---|---|
| Success | green | #00B42A |
| Warning | orange | #FF7D00 |
| Danger / error | red | #F53F3F |
| Primary / link / info | arcoblue | #165DFF |

**Extended data palette (base step 6)**

| Name | Hex |
|---|---|
| orangered | #F77234 |
| orange | #FF7D00 |
| gold | #F7BA1E |
| yellow | #FADC19 |
| lime | #9FDB1D |
| green | #00B42A |
| cyan | #14C9C9 |
| arcoblue | #165DFF |
| purple | #722ED1 |
| pinkpurple | #D91AD9 |
| magenta | #F5319D |
| red | #F53F3F |

Each of these has the same 10-step structure. Use them for charts, tags, and categorical data, not for general chrome.

**Green scale (success)**: #E8FFEA, #AFF0B5, #7BE188, #4CD263, #23C343, #00B42A, #009A29, #008026, #006622, #004D1C.

**Red scale (danger)**: #FFECE8, #FDCDC5, #FBACA3, #F98981, #F76560, #F53F3F, #CB2634, #A1151E, #770508, #4D000A.

**Neutral gray scale**

| Step | Hex | Typical use |
|---|---|---|
| gray-1 | #F7F8FA | Page background, subtle fills |
| gray-2 | #F2F3F5 | Hover fills, light borders, input background |
| gray-3 | #E5E6EB | Standard borders, dividers |
| gray-4 | #C9CDD4 | Strong borders, disabled text, placeholder |
| gray-5 | #A9AEB8 | |
| gray-6 | #86909C | Tertiary text |
| gray-7 | #6B7785 | |
| gray-8 | #4E5969 | Secondary text |
| gray-9 | #272E3B | |
| gray-10 | #1D2129 | Primary text |

### 4.2 Semantic color tokens (light mode)

Prefer these semantic tokens over raw palette steps.

| Token | Value | Use |
|---|---|---|
| `--color-bg-1` | #FFFFFF | Page and card surface |
| `--color-bg-2` | #FFFFFF | Container surface |
| `--color-bg-3` | #FFFFFF | Floating surface (dropdowns, popovers) |
| `--color-bg-white` | #FFFFFF | Always white |
| `--color-text-1` | gray-10 (#1D2129) | Headings, primary text |
| `--color-text-2` | gray-8 (#4E5969) | Body and secondary text |
| `--color-text-3` | gray-6 (#86909C) | Descriptions, helper text |
| `--color-text-4` | gray-4 (#C9CDD4) | Disabled and placeholder text |
| `--color-border-1` | gray-2 (#F2F3F5) | Subtle borders |
| `--color-border-2` | gray-3 (#E5E6EB) | Default borders |
| `--color-border-3` | gray-4 (#C9CDD4) | Strong borders |
| `--color-border-4` | gray-6 (#86909C) | Emphasis borders |
| `--color-fill-1` | gray-1 (#F7F8FA) | Lightest fill |
| `--color-fill-2` | gray-2 (#F2F3F5) | Default fill, hover |
| `--color-fill-3` | gray-3 (#E5E6EB) | Pressed fill |
| `--color-fill-4` | gray-4 (#C9CDD4) | Strong fill |
| `--primary-6` | arcoblue-6 | Primary actions |
| `--success-6` | green-6 | Success |
| `--warning-6` | orange-6 | Warning |
| `--danger-6` | red-6 | Danger |
| `--link-6` | arcoblue-6 | Links |

State convention for a colored control (primary example):

- Default: step 6
- Hover: step 5
- Active/pressed: step 7
- Disabled: step 3
- Light background (for tags, selected rows, light buttons): step 1
- Light hover background: step 2

### 4.3 Dark mode

Dark mode is activated by setting an attribute on the body:

```ts
document.body.setAttribute('arco-theme', 'dark');   // enable
document.body.removeAttribute('arco-theme');         // disable
```

Approximate dark surface and text tokens:

| Token | Value |
|---|---|
| `--color-bg-1` | #17171A |
| `--color-bg-2` | #232324 |
| `--color-bg-3` | #2A2A2B |
| `--color-bg-4` | #313132 |
| `--color-bg-5` | #373739 |
| `--color-text-1` | rgba(255,255,255,0.9) |
| `--color-text-2` | rgba(255,255,255,0.7) |
| `--color-text-3` | rgba(255,255,255,0.5) |
| `--color-text-4` | rgba(255,255,255,0.3) |
| `--color-border-2` | #333335 |

In dark mode the palette is re-derived so primary becomes slightly lighter (around #3C7EFF) to keep contrast. Do not invert colors manually; rely on the tokens and the `arco-theme` attribute.

### 4.4 Typography

Font stack (default):

```css
font-family: Inter, -apple-system, BlinkMacSystemFont, 'PingFang SC',
  'Hiragino Sans GB', 'noto sans', 'Microsoft YaHei', 'Helvetica Neue',
  Helvetica, 'Source Han Sans SC', 'Noto Sans CJK SC', 'WenQuanYi Micro Hei',
  Arial, sans-serif;
```

Monospace for code: `'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace`.

Type scale:

| Role | Size | Weight | Notes |
|---|---|---|---|
| Caption | 12px | 400 | Helper text, timestamps |
| Body 1 | 12px | 400 | Dense UI text |
| Body 2 | 13px | 400 | |
| Body 3 (default) | 14px | 400 | Default body and control text |
| Title 1 | 16px | 500 | Card and section titles |
| Title 2 | 20px | 500 | Page subtitles |
| Title 3 | 24px | 500 | Page titles |
| Display 1 | 36px | 500 | Marketing or hero numbers |
| Display 2 | 48px | 500 | |
| Display 3 | 56px | 500 | |

Rules:

- Default body line-height is about 1.5715.
- Weights: regular 400, medium 500, semibold 600. Avoid weights above 600.
- Headings use `--color-text-1`; body uses `--color-text-2`; helper text uses `--color-text-3`.
- Use the `Typography` component (`Typography.Title`, `Typography.Paragraph`, `Typography.Text`) for headings and copy where possible. It supports ellipsis, copy, and edit.

### 4.5 Spacing

Base unit is 4px.

| Token name | Value |
|---|---|
| 1 | 4px |
| 2 | 8px |
| 3 | 12px |
| 4 | 16px |
| 5 | 20px |
| 6 | 24px |
| 8 | 32px |
| 10 | 40px |
| 12 | 48px |

Common applications:

- Between an icon and its label: 8px.
- Between form items vertically: 20px to 24px.
- Card inner padding: 16px to 24px.
- Page content padding in admin layouts: 20px to 24px.
- Gap between adjacent cards: 16px.
- Use the `Space` component for consistent gaps between inline elements instead of manual margins.

### 4.6 Border radius

| Token | Value | Use |
|---|---|---|
| `--border-radius-none` | 0 | Tables, flush layouts |
| `--border-radius-small` | 2px | Default for Button, Input, Select, Tag, Checkbox |
| `--border-radius-medium` | 4px | Cards, Popovers, Dropdowns, Tooltips |
| `--border-radius-large` | 8px | Modals, large cards |
| `--border-radius-circle` | 50% | Avatars, circle buttons, Badge dots |

### 4.7 Sizing

| Size | Control height | Typical font | Horizontal padding |
|---|---|---|---|
| mini | 24px | 12px | 11px |
| small | 28px | 14px | 15px |
| default | 32px | 14px | 15px |
| large | 36px | 14px | 19px |

Icon sizes: 12, 14 (default inside controls), 16, 18, 20, 24, 32, 48 depending on context.

### 4.8 Shadows (floating layers only)

Shadows are soft and low-opacity. Approximate values:

| Level | Value | Use |
|---|---|---|
| shadow-1 | 0 2px 5px rgba(0,0,0,0.1) | Small raised elements |
| shadow-2 | 0 4px 10px rgba(0,0,0,0.1) | Dropdowns, popovers, hover cards |
| shadow-3 | 0 6px 16px rgba(0,0,0,0.08) | Modals, drawers, notifications |

Avoid stacking shadows and avoid colored shadows.

### 4.9 Motion

- Standard easing: `cubic-bezier(0.34, 0.69, 0.1, 1)`.
- Overshoot easing (used for popups): `cubic-bezier(0.3, 1.3, 0.3, 1)`.
- Durations: 100ms for micro state changes (hover color), 200ms for most transitions, 300ms for panels, drawers, and modals.
- Animate opacity and transform. Avoid animating layout properties.
- Respect `prefers-reduced-motion`.

---

## 5. Component Catalog

Use this map to choose the right component. All names are exports from `@arco-design/web-react`.

**General**
- `Button`, `Icon` (via `/icon`), `Typography`

**Layout**
- `Divider`, `Grid` (`Row`, `Col`, 24-column), `Layout` (`Header`, `Sider`, `Content`, `Footer`), `Space`, `ResizeBox`

**Navigation**
- `Anchor`, `Breadcrumb`, `Dropdown`, `Menu`, `PageHeader`, `Pagination`, `Steps`, `Tabs` (also listed under data display)

**Data entry**
- `AutoComplete`, `Cascader`, `Checkbox`, `ColorPicker`, `DatePicker`, `Form`, `Input`, `InputNumber`, `InputTag`, `Mentions`, `Radio`, `Rate`, `Select`, `Slider`, `Switch`, `TimePicker`, `Transfer`, `TreeSelect`, `Upload`

**Data display**
- `Avatar`, `Badge`, `Calendar`, `Card`, `Carousel`, `Collapse`, `Comment`, `Descriptions`, `Empty`, `Image`, `List`, `Popover`, `Statistic`, `Table`, `Tabs`, `Tag`, `Timeline`, `Tooltip`, `Tree`

**Feedback**
- `Alert`, `Drawer`, `Message`, `Modal`, `Notification`, `Popconfirm`, `Progress`, `Result`, `Skeleton`, `Spin`

**Other**
- `Affix`, `BackTop`, `ConfigProvider`, `Trigger`, `VirtualList`, `Watermark`

### Choosing between similar components

| Need | Use |
|---|---|
| Transient confirmation after an action | `Message` |
| Richer transient message with title and description | `Notification` |
| Persistent inline status banner | `Alert` |
| Blocking decision or short form | `Modal` |
| Longer form or detail view without losing page context | `Drawer` |
| Lightweight confirm on a single control | `Popconfirm` |
| Explanatory hint on hover | `Tooltip` |
| Small rich content on hover or click | `Popover` |
| Whole-page outcome (success, 404, 403, 500) | `Result` |
| Nothing to show yet | `Empty` |
| Content loading, layout known | `Skeleton` |
| Action or region loading | `Spin` |
| Tabular data with sort, filter, pagination | `Table` |
| Key-value details | `Descriptions` |
| Repeating items with meta and actions | `List` |
| Summary number with label and trend | `Statistic` |

---

## 6. Component Patterns

### 6.1 Button

Types: `primary` (filled blue), `secondary` (light gray fill, the default-looking button), `outline`, `dashed`, `text`.
Status: `warning`, `danger`, `success` (combine with a type, for example `type="primary" status="danger"`).
Shapes: `square` (default), `round`, `circle`.
Sizes: `mini`, `small`, `default`, `large`.

```tsx
<Space>
  <Button type="primary">Save</Button>
  <Button type="secondary">Cancel</Button>
  <Button type="outline">Export</Button>
  <Button type="text">Learn more</Button>
  <Button type="primary" status="danger">Delete</Button>
  <Button type="primary" icon={<IconPlus />}>New</Button>
  <Button loading>Submitting</Button>
</Space>
```

Guidance:
- Place the primary action on the right in dialogs and form footers, with cancel to its left.
- Use `type="text"` for low-emphasis actions in table rows and toolbars.
- Icon-only buttons must use `shape="circle"` or have an `aria-label`, and should have a Tooltip.

### 6.2 Form

Use `Form` with `Form.Item` for all data entry. Labels default to a side-aligned or top-aligned layout; prefer `layout="vertical"` for narrow containers and `layout="horizontal"` (label on left) for dense admin pages.

```tsx
const [form] = Form.useForm();

<Form form={form} layout="vertical" onSubmit={(values) => console.log(values)}>
  <Form.Item label="Username" field="username" rules={[{ required: true, message: 'Required' }]}>
    <Input placeholder="Enter username" />
  </Form.Item>
  <Form.Item label="Role" field="role">
    <Select options={['Admin', 'Editor', 'Viewer']} />
  </Form.Item>
  <Form.Item>
    <Space>
      <Button type="primary" htmlType="submit">Submit</Button>
      <Button onClick={() => form.resetFields()}>Reset</Button>
    </Space>
  </Form.Item>
</Form>
```

Guidance:
- Required fields show a red asterisk automatically through `rules`.
- Show validation messages inline under the field.
- Group related fields with `Grid` rows; use 24-column spans.

### 6.3 Table

```tsx
<Table
  columns={columns}
  data={data}
  rowKey="id"
  border={{ wrapper: true, cell: false }}
  pagination={{ pageSize: 10, showTotal: true, sizeCanChange: true }}
  loading={loading}
/>
```

Guidance:
- Header background is a light gray fill; row hover uses `--color-fill-1`.
- Put row actions in the last column as text buttons, with destructive actions in red.
- Right-align numeric columns; left-align text.
- Use `Tag` for status columns with semantic colors.
- Provide a filter or search bar above the table, and primary page actions at the top right.

### 6.4 Layout shell (admin pattern, following Arco Pro)

```
+----------------------------------------------------------+
| Navbar (height 60px, bg-2, bottom border)                |
+------------+---------------------------------------------+
| Sider      | Content (padding 20-24px, bg: gray-1)       |
| width 220  |   PageHeader / Breadcrumb                   |
| (48 when   |   Card(s) with 16px gaps                    |
| collapsed) |                                             |
+------------+---------------------------------------------+
```

- Navbar: 60px high, logo left, search and user menu right.
- Sider: 220px wide, collapsible to 48px, uses `Menu` with icons.
- Content background: `--color-fill-2` or gray-1; cards sit on it as white (`--color-bg-2`) surfaces.
- Breadcrumb above the page title; page actions aligned right of the title.

### 6.5 Card

- White surface, 1px `--color-neutral-3` border or none, radius 4px, padding 16px to 24px.
- Optional header with `Typography.Title` at heading level 6 or a 16px medium title, and an `extra` slot for links or actions.
- Do not add shadow by default; use `hoverable` only for clickable cards.

### 6.6 Modal and Drawer

- Modal default width 520px; title 16px medium; footer with Cancel then Confirm (primary).
- Use `Modal.confirm` / `Modal.warning` / `Modal.error` for quick decisions.
- Destructive confirm buttons use `status="danger"`.
- Drawer default width 400px to 600px for forms or details, opened from the right.

### 6.7 Feedback

```tsx
Message.success('Saved');
Message.error('Something went wrong');
Notification.info({ title: 'Update available', content: 'Version 2.0 is ready.' });
<Alert type="warning" content="Your trial ends in 3 days." closable />
<Result status="404" title="Page not found" subTitle="Check the URL and try again." />
```

### 6.8 Tag and Badge

- `Tag` colors map to the data palette (`color="arcoblue"`, `"green"`, `"orange"`, `"red"`, `"gray"`). Use light fills (step 1 background, step 6 text) for status labels.
- `Badge` for counts and dots on icons and avatars; status dot colors follow semantic colors.

---

## 7. Theming and Customization

Three levels, from least to most invasive:

1. **Design Lab** (https://arco.design/themes): recommended. Build and publish a theme visually, then install it as a package (for example `@arco-themes/react-your-theme`) and import it through the bundler plugin.
2. **less-loader with `modifyVars`**: override Less variables at build time.

```js
// webpack
{
  loader: 'less-loader',
  options: {
    lessOptions: {
      modifyVars: {
        'arcoblue-6': '#0FC6C2',
        'border-radius-small': '4px',
      },
      javascriptEnabled: true,
    },
  },
}
```

3. **CSS variable overrides at runtime**: override the exposed variables on `body` or a scoped container.

```css
body {
  --primary-6: 15, 198, 194;   /* RGB triplet, no rgb() wrapper */
}
```

Rules:
- When changing the primary color, change the whole scale (steps 1 to 10) or let Design Lab generate it, so hover, active, and disabled states stay coherent.
- Do not override individual component styles with `!important`. Use the component's `className`, `style`, or documented theme variables.
- Keep contrast at WCAG AA or better for text on colored backgrounds.

---

## 8. Iconography

- Use the Arco icon set from `@arco-design/web-react/icon`. Names follow `Icon` plus PascalCase, for example `IconSearch`, `IconDelete`, `IconPlus`, `IconSettings`, `IconUser`, `IconDown`, `IconRight`, `IconClose`, `IconCheck`.
- Style: 2px stroke, rounded joins, outline by default, 24px grid.
- Icons inherit `currentColor`; do not hardcode fill colors.
- Pair an icon with text where possible; icon-only controls need a Tooltip and an accessible label.
- Custom icons can be managed on Icon Box (https://arco.design/iconbox) and consumed through `Icon.addFromIconFontCn` or generated components.

---

## 9. Accessibility

- All interactive elements must be keyboard reachable with a visible focus ring (Arco provides a primary-colored focus outline; do not remove it).
- Maintain text contrast of at least 4.5:1 for body text. `--color-text-3` on white is intended for secondary text only; do not use `--color-text-4` for meaningful content.
- Provide `aria-label` for icon-only buttons and inputs without visible labels.
- Do not use color as the only carrier of meaning (pair status colors with an icon or text).
- Modals and drawers must trap focus and close on Escape (default behavior; keep it).

---

## 10. Responsive Behavior

- `Grid` uses 24 columns with breakpoints: xs < 576px, sm 576px, md 768px, lg 992px, xl 1200px, xxl 1600px.
- Use `Row` and `Col` with `xs`, `sm`, `md`, `lg`, `xl` props for responsive spans.
- Collapse the Sider to icons (48px) or a Drawer on narrow viewports.
- Tables that overflow should use `scroll={{ x: true }}` with fixed first and last columns where appropriate.
- Browser support: Edge 16+, Firefox 31+, Chrome 49+, Safari 31+, Opera 36+, Electron, and the last two versions of modern browsers.

---

## 11. Code Conventions

- Language: TypeScript, function components, hooks.
- CSS class prefix for Arco components is `arco-` (for example `arco-btn`, `arco-input`). Never reuse this prefix for custom classes; use your own prefix.
- Prefer composition via props (`icon`, `prefix`, `suffix`, `extra`, `footer`) over wrapping and restyling.
- Prefer controlled state with `value` and `onChange`, or `Form` with `field` names.
- Import components by name from the package root; the bundler plugin handles tree-shaking.
- Do not mix Arco with another full component library (such as Ant Design or MUI) on the same page; the visual languages conflict.
- When layout styling is needed, use CSS variables for color and spacing so dark mode keeps working:

```css
.panel {
  background: var(--color-bg-2);
  color: var(--color-text-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium);
  padding: 16px 20px;
}
.panel__title {
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 12px;
}
```

---

## 12. Checklist Before Delivering UI

Run through this list before delivering UI code or a design:

1. Are all controls real Arco components, not hand-built lookalikes?
2. Is there exactly one primary button per region?
3. Are all colors tokens or palette steps, with no stray hex values?
4. Do spacing values fall on the 4px grid?
5. Do control heights match one size level per row?
6. Are border radii 2px for controls and 4px to 8px for containers?
7. Are shadows limited to floating layers?
8. Is the text color hierarchy correct (text-1 headings, text-2 body, text-3 helper)?
9. Are loading, empty, and error states handled?
10. Does the layout work in dark mode with the `arco-theme="dark"` attribute?
11. Are icon-only buttons labelled and equipped with a Tooltip?
12. Are form fields validated with inline messages and required markers?
13. Are numeric table columns right-aligned and status shown with Tag plus text?
14. Is the page responsive using Grid breakpoints?

---

## 13. Do and Do Not

**Do**
- Use a calm neutral base with a single blue accent.
- Keep interfaces dense but breathable: 14px text, 32px controls, 16px to 24px gaps.
- Use light tinted fills (step 1) for selected, hover, and tag backgrounds.
- Use `Space`, `Grid`, and `Layout` for structure.
- Use thin 1px borders in `--color-border-2` to separate regions.

**Do not**
- Do not use gradients, glassmorphism, heavy drop shadows, or neon colors.
- Do not use more than one accent color for general chrome.
- Do not use large rounded corners on inputs and buttons.
- Do not use pure black (#000) text or pure gray borders darker than gray-4 for standard separators.
- Do not center-align long text or table content.
- Do not hide primary actions inside overflow menus.
- Do not invent new status colors; map everything to success, warning, danger, or primary.

---

## 14. Ecosystem and Reference Links

| Resource | URL |
|---|---|
| Documentation | https://arco.design |
| React components | https://arco.design/react/docs/overview |
| Dark mode | https://arco.design/react/docs/dark |
| Theme customization | https://arco.design/react/docs/theme |
| Design Lab (theme builder) | https://arco.design/themes |
| Material Market | https://arco.design/material |
| Icon Box | https://arco.design/iconbox |
| Arco Pro (app scaffold) | https://arco.design/pro/ |
| Vue library | https://arco.design/vue/docs/start |
| Figma component library | https://www.figma.com/file/M66cTiLXHa4SVyZIlfY5Pb/arco-Design-System |
| Awesome Arco | https://github.com/arco-design/awesome-arco |
| Bundler plugins | https://github.com/arco-design/arco-plugins |
| Repository | https://github.com/arco-design/arco-design |

---

## 15. Notes on Accuracy

The repository README confirms: 60+ components, customizable design tokens (via less-loader or Design Lab), reusable materials, TypeScript support, dark mode, and the npm package `@arco-design/web-react`. The detailed token values in this guide (palette hex codes, type scale, radii, control heights, shadows, motion curves) reflect the library defaults as commonly documented, and a few values (shadows, some dark-mode surfaces, exact padding) are approximations. If an exact value matters, verify it against the installed package's Less variables or the live documentation at https://arco.design.