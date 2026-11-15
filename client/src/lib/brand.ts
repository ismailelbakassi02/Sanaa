import { derivePrimaryScale, hexToRgb, toCssTriplet, type ColorMode, type Rgb } from './color';

/**
 * The single source of the SANAA brand color. Components never repeat the hex:
 * they use `rgb(var(--sanaa-brand-rgb))` or an Arco primary step.
 */
export const BRAND_NAME = 'SANAA';
export const BRAND_COLOR = '#195A56';

export const BRAND_RGB: Rgb = hexToRgb(BRAND_COLOR) ?? { r: 25, g: 90, b: 86 };

/** Sets the brand triplet and the derived Arco scale on the body element. */
export function applyBrandTheme(mode: ColorMode): void {
  const body = document.body;
  if (!body) {
    return;
  }
  body.style.setProperty('--sanaa-brand-rgb', toCssTriplet(BRAND_RGB));
  const scale = derivePrimaryScale(BRAND_COLOR, mode);
  if (!scale) {
    return;
  }
  for (const [step, triplet] of Object.entries(scale)) {
    body.style.setProperty(`--primary-${step}`, triplet);
  }
}

/** Reads the computed brand triplet, mainly for tests and the privacy page. */
export function brandTriplet(): string {
  return toCssTriplet(BRAND_RGB);
}
