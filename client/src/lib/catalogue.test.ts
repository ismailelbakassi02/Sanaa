import { describe, expect, it } from 'vitest';

import { formatRating, priceLabel, priceView, ratingLabel } from './catalogue';

const base = {
  price: 200,
  effectivePrice: 150,
  currency: 'MAD',
  discountActive: true,
};

describe('priceView', () => {
  it('shows an active discount with its rounded percentage', () => {
    expect(priceView(base)).toEqual({
      kind: 'discount',
      current: 150,
      original: 200,
      percent: 25,
      currency: 'MAD',
    });
  });

  it('ignores an expired promotion even when effectivePrice is still lower', () => {
    expect(priceView({ ...base, discountActive: false })).toEqual({
      kind: 'paid',
      current: 200,
      original: null,
      percent: null,
      currency: 'MAD',
    });
  });

  it('treats a zero price as free and never shows a strikethrough', () => {
    expect(
      priceView({ price: 0, effectivePrice: 0, currency: 'MAD', discountActive: true }),
    ).toEqual({
      kind: 'free',
      current: 0,
      original: null,
      percent: null,
      currency: 'MAD',
    });
  });

  it('keeps the original price when the promotion is not a real reduction', () => {
    expect(
      priceView({ price: 100, effectivePrice: 100, currency: 'MAD', discountActive: true }),
    ).toEqual({
      kind: 'paid',
      current: 100,
      original: null,
      percent: null,
      currency: 'MAD',
    });
  });
});

describe('priceLabel', () => {
  it('formats the effective amount through Intl in French', () => {
    // Non-breaking spaces from Intl are normalised so the assertion stays readable.
    expect(priceLabel(base).replace(/\u202f|\u00a0/g, ' ')).toBe('150,00 MAD');
  });

  it('renders a free course as "Gratuit"', () => {
    expect(priceLabel({ price: 0, effectivePrice: 0, currency: 'MAD', discountActive: false })).toBe(
      'Gratuit',
    );
  });
});

describe('ratingLabel', () => {
  it('uses the French comma and the review count', () => {
    expect(ratingLabel(4.7, 3)).toBe('4,7 sur 5, 3 avis');
  });

  it('calls a course without a visible review "Nouveau"', () => {
    expect(ratingLabel(0, 0)).toBe('Nouveau');
    expect(ratingLabel(5, 0)).toBe('Nouveau');
  });

  it('formats the average with one decimal', () => {
    expect(formatRating(5)).toBe('5,0');
  });
});
