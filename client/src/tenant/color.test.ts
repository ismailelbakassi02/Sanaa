import { describe, expect, it } from 'vitest';

import { derivePrimaryScale, hexToRgb, toCssTripletFromHex, type Rgb } from './color';

function luminance(color: Rgb): number {
  const channel = (value: number) => {
    const srgb = value / 255;
    return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
}

function parseTriplet(triplet: string): Rgb {
  const [r, g, b] = triplet.split(',').map((part) => Number.parseInt(part.trim(), 10));
  return { r, g, b };
}

describe('hexToRgb', () => {
  it('parses a six-digit hex color', () => {
    expect(hexToRgb('#00B42A')).toEqual({ r: 0, g: 180, b: 42 });
    expect(hexToRgb('#722ED1')).toEqual({ r: 114, g: 46, b: 209 });
  });

  it('expands a three-digit hex color', () => {
    expect(hexToRgb('#0a0')).toEqual({ r: 0, g: 170, b: 0 });
  });

  it('returns null for invalid input', () => {
    expect(hexToRgb('')).toBeNull();
    expect(hexToRgb('00B42A')).toBeNull();
    expect(hexToRgb('#12345')).toBeNull();
    expect(hexToRgb('#GGGGGG')).toBeNull();
    expect(hexToRgb('rgb(0, 180, 42)')).toBeNull();
  });
});

describe('toCssTripletFromHex', () => {
  it('returns the RGB triplet Arco variables expect', () => {
    expect(toCssTripletFromHex('#00B42A')).toBe('0, 180, 42');
  });

  it('returns null for invalid input', () => {
    expect(toCssTripletFromHex('not-a-color')).toBeNull();
  });
});

describe('derivePrimaryScale', () => {
  it('returns ten steps and keeps white text readable on step 6', () => {
    const scale = derivePrimaryScale('#00B42A');
    expect(scale).not.toBeNull();
    if (!scale) {
      return;
    }
    expect(Object.keys(scale)).toHaveLength(10);
    const base = parseTriplet(scale[6]);
    const contrast = 1.05 / (luminance(base) + 0.05);
    expect(contrast).toBeGreaterThanOrEqual(4.5);
    // The demo green is too light for white text, so it must be darkened.
    expect(base.g).toBeLessThan(180);
  });

  it('keeps a color that already has enough contrast', () => {
    const scale = derivePrimaryScale('#722ED1');
    expect(scale).not.toBeNull();
    if (!scale) {
      return;
    }
    expect(parseTriplet(scale[6])).toEqual({ r: 114, g: 46, b: 209 });
  });

  it('returns null for invalid input', () => {
    expect(derivePrimaryScale('green')).toBeNull();
  });
});
