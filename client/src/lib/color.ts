export interface Rgb {
  r: number;
  g: number;
  b: number;
}

export type ColorMode = 'light' | 'dark';
export type PrimaryScale = Record<number, string>;

const WHITE: Rgb = { r: 255, g: 255, b: 255 };
const BLACK: Rgb = { r: 0, g: 0, b: 0 };
const DARK_SURFACE: Rgb = { r: 23, g: 23, b: 26 };

/** Parses #rgb or #rrggbb. Returns null for anything else. */
export function hexToRgb(hex: string): Rgb | null {
  const value = hex.trim();
  const match = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.exec(value);
  if (!match) {
    return null;
  }
  const digits = match[1];
  const expanded =
    digits.length === 3
      ? digits
          .split('')
          .map((digit) => digit + digit)
          .join('')
      : digits;
  return {
    r: Number.parseInt(expanded.slice(0, 2), 16),
    g: Number.parseInt(expanded.slice(2, 4), 16),
    b: Number.parseInt(expanded.slice(4, 6), 16),
  };
}

export function isHexColor(value: string): boolean {
  return hexToRgb(value) !== null;
}

/** Formats a color as the "r, g, b" triplet Arco CSS variables expect. */
export function toCssTriplet(rgb: Rgb): string {
  return `${rgb.r}, ${rgb.g}, ${rgb.b}`;
}

export function toCssTripletFromHex(hex: string): string | null {
  const rgb = hexToRgb(hex);
  return rgb ? toCssTriplet(rgb) : null;
}

function channel(value: number): number {
  const srgb = value / 255;
  return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
}

export function relativeLuminance(color: Rgb): number {
  return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
}

/** WCAG 2.1 contrast ratio. Order does not matter. */
export function contrastRatio(first: Rgb, second: Rgb): number {
  const a = relativeLuminance(first);
  const b = relativeLuminance(second);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

export function contrastWithWhite(color: Rgb): number {
  return contrastRatio(color, WHITE);
}

/** White text on a colored fill is the only combination used for primary buttons. */
export function whiteTextContrast(hex: string): number | null {
  const rgb = hexToRgb(hex);
  return rgb ? contrastWithWhite(rgb) : null;
}

export function hasReadableWhiteText(hex: string): boolean {
  const ratio = whiteTextContrast(hex);
  return ratio !== null && ratio >= 4.5;
}

/** Contrast of a color against the dark page surface, used for links and focus rings. */
export function darkSurfaceContrast(hex: string): number | null {
  const rgb = hexToRgb(hex);
  return rgb ? contrastRatio(rgb, DARK_SURFACE) : null;
}

function mix(color: Rgb, other: Rgb, ratio: number): Rgb {
  return {
    r: Math.round(color.r + (other.r - color.r) * ratio),
    g: Math.round(color.g + (other.g - color.g) * ratio),
    b: Math.round(color.b + (other.b - color.b) * ratio),
  };
}

/**
 * Builds the ten Arco primary steps from the single brand color.
 *
 * Light mode: step 6 is the brand itself, steps 1-5 are tints and 7-10 shades.
 * The brand is darkened only when white text on step 6 would miss WCAG AA.
 *
 * Dark mode follows the same step numbering but with different intent: step 6
 * is slightly lightened so the filled button separates from the dark surface,
 * step 5 is the lighter hover fill and step 7 the darker pressed fill. Links
 * and focus rings use step 8, which stays above 4.5:1 against #17171A.
 */
export function derivePrimaryScale(hex: string, mode: ColorMode = 'light'): PrimaryScale | null {
  const brand = hexToRgb(hex);
  if (!brand) {
    return null;
  }
  if (mode === 'light') {
    const base = darkenForContrast(brand, 4.5);
    return {
      1: toCssTriplet(mix(base, WHITE, 0.92)),
      2: toCssTriplet(mix(base, WHITE, 0.8)),
      3: toCssTriplet(mix(base, WHITE, 0.68)),
      4: toCssTriplet(mix(base, WHITE, 0.5)),
      5: toCssTriplet(mix(base, WHITE, 0.15)),
      6: toCssTriplet(base),
      7: toCssTriplet(mix(base, BLACK, 0.15)),
      8: toCssTriplet(mix(base, BLACK, 0.3)),
      9: toCssTriplet(mix(base, BLACK, 0.45)),
      10: toCssTriplet(mix(base, BLACK, 0.6)),
    };
  }
  return {
    1: toCssTriplet(mix(brand, BLACK, 0.75)),
    2: toCssTriplet(mix(brand, BLACK, 0.6)),
    3: toCssTriplet(mix(brand, BLACK, 0.45)),
    4: toCssTriplet(mix(brand, BLACK, 0.3)),
    5: toCssTriplet(mix(brand, WHITE, 0.15)),
    6: toCssTriplet(mix(brand, WHITE, 0.06)),
    7: toCssTriplet(brand),
    8: toCssTriplet(mix(brand, WHITE, 0.35)),
    9: toCssTriplet(mix(brand, WHITE, 0.5)),
    10: toCssTriplet(mix(brand, WHITE, 0.62)),
  };
}

/** Pick readable text for an arbitrary publisher accent used as a flat fill. */
export function readableAccent(hex: string): { background: string; text: string } | null {
  const rgb = hexToRgb(hex);
  if (!rgb) {
    return null;
  }
  const background = `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`;
  const text = contrastWithWhite(rgb) >= 4.5 ? 'rgb(255, 255, 255)' : 'rgb(0, 0, 0)';
  return { background, text };
}

function darkenForContrast(color: Rgb, target: number): Rgb {
  if (contrastWithWhite(color) >= target) {
    return color;
  }
  let low = 0;
  let high = 1;
  for (let step = 0; step < 24; step += 1) {
    const middle = (low + high) / 2;
    if (contrastWithWhite(scale(color, middle)) >= target) {
      low = middle;
    } else {
      high = middle;
    }
  }
  return scale(color, low);
}

function scale(color: Rgb, factor: number): Rgb {
  return {
    r: Math.round(color.r * factor),
    g: Math.round(color.g * factor),
    b: Math.round(color.b * factor),
  };
}
