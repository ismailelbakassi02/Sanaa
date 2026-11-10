import { describe, expect, it } from 'vitest';

import { formatDuration, formatPrice } from './format';

describe('formatDuration', () => {
  it('formats seconds as mm:ss', () => {
    expect(formatDuration(0)).toBe('00:00');
    expect(formatDuration(59)).toBe('00:59');
    expect(formatDuration(90)).toBe('01:30');
    expect(formatDuration(600)).toBe('10:00');
  });

  it('keeps minutes above 59 rather than adding an hours segment', () => {
    expect(formatDuration(3661)).toBe('61:01');
  });

  it('floors fractional seconds and clamps invalid values', () => {
    expect(formatDuration(30.9)).toBe('00:30');
    expect(formatDuration(-5)).toBe('00:00');
    expect(formatDuration(Number.NaN)).toBe('00:00');
  });
});

describe('formatPrice', () => {
  it('renders a free course as "Gratuit"', () => {
    expect(formatPrice(0)).toBe('Gratuit');
  });

  it('formats a price in MAD through Intl fr-FR', () => {
    const formatted = formatPrice(349);
    expect(formatted).toContain('349');
    expect(formatted).toContain('MAD');
  });

  it('renders decimals with a French comma', () => {
    expect(formatPrice(1299.5)).toContain('1');
    expect(formatPrice(1299.5)).toContain(',50');
  });
});
