import { describe, expect, it } from 'vitest';

import { isReservedSlug, isValidSlug, normalizeSlug, slugFromName } from './slug';

describe('slugFromName', () => {
  it('strips accents and turns separators into single hyphens', () => {
    expect(slugFromName('Atelier Lumière & Co')).toBe('atelier-lumiere-co');
    expect(slugFromName('  École   du  Savoir  ')).toBe('ecole-du-savoir');
  });

  it('caps the candidate at 63 characters and never ends with a hyphen', () => {
    const candidate = slugFromName('a'.repeat(70));
    expect(candidate).toHaveLength(63);
    expect(candidate.endsWith('-')).toBe(false);
  });

  it('returns an empty string when nothing usable is left', () => {
    expect(slugFromName('...')).toBe('');
  });
});

describe('isValidSlug', () => {
  it('accepts a normal lowercase slug', () => {
    expect(isValidSlug('atelier-lumiere')).toBe(true);
    expect(isValidSlug('a1b')).toBe(true);
  });

  it('normalizes case and surrounding spaces before validating', () => {
    expect(normalizeSlug('  Atelier  ')).toBe('atelier');
    expect(isValidSlug('  Atelier  ')).toBe(true);
  });

  it('rejects lengths outside 3-63 and edge hyphens', () => {
    expect(isValidSlug('ab')).toBe(false);
    expect(isValidSlug('a'.repeat(63))).toBe(true);
    expect(isValidSlug('a'.repeat(64))).toBe(false);
    expect(isValidSlug('-atelier')).toBe(false);
    expect(isValidSlug('atelier-')).toBe(false);
  });

  it('rejects platform routes and punycode look-alikes', () => {
    expect(isReservedSlug('admin')).toBe(true);
    expect(isValidSlug('admin')).toBe(false);
    expect(isValidSlug('xn--atelier')).toBe(false);
  });
});
