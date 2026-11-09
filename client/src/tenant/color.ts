export interface Rgb {
  r: number;
  g: number;
  b: number;
}

export type PrimaryScale = Record<number, string>;

const WHITE: Rgb = { r: 255, g: 255, b: 255 };
const BLACK: Rgb = { r: 0, g: 0, b: 0 };

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

/** Formats a color as the "r, g, b" triplet Arco CSS variables expect. */
export function toCssTriplet(rgb: Rgb): string {
  return `${rgb.r}, ${rgb.g}, ${rgb.b}`;
}

export function toCssTripletFromHex(hex: string): string | null {
  const rgb = hexToRgb(hex);
  return rgb ? toCssTriplet(rgb) : null;
}

/**
 * Builds the ten primary steps Arco expects. Step 6 is darkened until white
 * text on it reaches WCAG AA (4.5:1), because tenant colors such as the demo
 * green do not provide enough contrast on their own. Steps 1 to 5 are tints,
 * 7 to 10 shades, so hover and pressed states stay coherent.
 */
export function derivePrimaryScale(hex: string): PrimaryScale | null {
  const brand = hexToRgb(hex);
  if (!brand) {
    return null;
  }
  const base = darkenForContrast(brand, 5.6);
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

function darkenForContrast(color: Rgb, target: number): Rgb {
  if (contrastWithWhite(color) >= target) {
    return color;
  }
  let low = 0;
  let high = 1;
  // Binary search the largest factor that still meets the contrast target, so
  // the resulting color stays as close as possible to the tenant brand.
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

function contrastWithWhite(color: Rgb): number {
  const luminance = relativeLuminance(color);
  return 1.05 / (luminance + 0.05);
}

function relativeLuminance(color: Rgb): number {
  const channel = (value: number) => {
    const srgb = value / 255;
    return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
}

function mix(color: Rgb, other: Rgb, ratio: number): Rgb {
  return {
    r: Math.round(color.r + (other.r - color.r) * ratio),
    g: Math.round(color.g + (other.g - color.g) * ratio),
    b: Math.round(color.b + (other.b - color.b) * ratio),
  };
}

function scale(color: Rgb, factor: number): Rgb {
  return {
    r: Math.round(color.r * factor),
    g: Math.round(color.g * factor),
    b: Math.round(color.b * factor),
  };
}
