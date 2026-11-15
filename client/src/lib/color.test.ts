import { describe, expect, it } from 'vitest';

import { BRAND_COLOR } from './brand';
import {
  contrastRatio,
  contrastWithWhite,
  derivePrimaryScale,
  hasReadableWhiteText,
  hexToRgb,
  isHexColor,
  readableAccent,
  relativeLuminance,
  whiteTextContrast,
  type Rgb,
} from './color';

function rgbFromTriplet(triplet: string): Rgb {
  const [r, g, b] = triplet.split(',').map((part) => Number.parseInt(part.trim(), 10));
  return { r, g, b };
}

const WHITE: Rgb = { r: 255, g: 255, b: 255 };
const BLACK: Rgb = { r: 0, g: 0, b: 0 };
/** Arco's dark page surface, the reference for dark-mode link contrast. */
const DARK_SURFACE: Rgb = { r: 23, g: 23, b: 26 };

describe('contrastRatio', () => {
  it('returns about 21:1 for black on white and 1:1 for a color on itself', () => {
    expect(contrastRatio(WHITE, BLACK)).toBeCloseTo(21, 0);
    const brand = hexToRgb(BRAND_COLOR) as Rgb;
    expect(contrastRatio(brand, brand)).toBeCloseTo(1, 5);
  });

  it('is symmetric', () => {
    const brand = hexToRgb(BRAND_COLOR) as Rgb;
    expect(contrastRatio(WHITE, brand)).toBeCloseTo(contrastRatio(brand, WHITE), 10);
  });
});

describe('brand contrast', () => {
  it('keeps white text readable on the SANAA brand fill', () => {
    // Measured value for white on #195A56: 7.96:1.
    expect(whiteTextContrast(BRAND_COLOR)).toBeCloseTo(7.96, 1);
    expect(hasReadableWhiteText(BRAND_COLOR)).toBe(true);
    expect(hasReadableWhiteText('#00B42A')).toBe(false);
  });

  it('derives a light scale whose base and hover pass WCAG AA text contrast', () => {
    const scale = derivePrimaryScale(BRAND_COLOR, 'light');
    expect(scale).not.toBeNull();
    const main = rgbFromTriplet(scale![6]);
    const hover = rgbFromTriplet(scale![5]);
    // Measured value for white on the light hover #3C736F: 5.42:1.
    expect(contrastWithWhite(main)).toBeGreaterThanOrEqual(4.5);
    expect(contrastWithWhite(hover)).toBeGreaterThanOrEqual(4.5);
    // Step 7 is the pressed fill, so it must stay darker than step 6.
    expect(relativeLuminance(rgbFromTriplet(scale![7]))).toBeLessThan(
      relativeLuminance(main),
    );
  });

  it('derives a dark scale whose link step stays readable on the dark surface', () => {
    const scale = derivePrimaryScale(BRAND_COLOR, 'dark');
    expect(scale).not.toBeNull();
    const link = rgbFromTriplet(scale![8]);
    // Measured value for #6A9491 against #17171A: 5.33:1.
    expect(contrastRatio(link, DARK_SURFACE)).toBeGreaterThanOrEqual(4.5);
    // Step 6 is slightly lightened so the filled button separates from the page.
    const base = rgbFromTriplet(scale![6]);
    const brand = hexToRgb(BRAND_COLOR) as Rgb;
    expect(relativeLuminance(base)).toBeGreaterThan(relativeLuminance(brand));
    // It is a fill, not text: it does not need 4.5:1 against the dark page.
    expect(contrastRatio(base, DARK_SURFACE)).toBeLessThan(4.5);
  });
});

describe('hex parsing and readable accents', () => {
  it('accepts #rgb and #rrggbb only', () => {
    expect(hexToRgb('#195A56')).toEqual({ r: 25, g: 90, b: 86 });
    expect(hexToRgb('#fff')).toEqual({ r: 255, g: 255, b: 255 });
    expect(hexToRgb('195A56')).toBeNull();
    expect(hexToRgb('#12345')).toBeNull();
    expect(isHexColor('#195A56')).toBe(true);
    expect(isHexColor('bleu')).toBe(false);
  });

  it('chooses readable text on an arbitrary publisher accent', () => {
    expect(readableAccent('#195A56')?.text).toBe('rgb(255, 255, 255)');
    expect(readableAccent('#FFD400')?.text).toBe('rgb(0, 0, 0)');
    expect(readableAccent('nope')).toBeNull();
  });
});
