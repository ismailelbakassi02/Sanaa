import type { PublicCourseCard } from '../api/catalogue';
import { fr } from '../i18n/fr';
import { formatPrice } from '../i18n/format';

/** One decimal, French comma: 4.7 becomes "4,7". */
const ratingFormatter = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export function formatRating(ratingAvg: number): string {
  return ratingFormatter.format(ratingAvg);
}

/**
 * The card rating line. A course without a visible review is "Nouveau": showing
 * "0 sur 5" would look like a bad score instead of an unknown one.
 */
export function ratingLabel(ratingAvg: number, ratingCount: number): string {
  if (ratingCount <= 0 || ratingAvg <= 0) {
    return fr.marketplace.newCourse;
  }
  return fr.marketplace.rating(formatRating(ratingAvg), ratingCount);
}

export interface PriceView {
  kind: 'free' | 'discount' | 'paid';
  current: number;
  original: number | null;
  percent: number | null;
  currency: string;
}

type PriceSource = Pick<
  PublicCourseCard,
  'price' | 'effectivePrice' | 'currency' | 'discountActive'
>;

/**
 * The single source of truth for a price block.
 *
 * WHY the discount is keyed on `discountActive` and not on
 * `effectivePrice < price`: once the deadline passes, the backend keeps
 * `effectivePrice` at the old promotional value until the next read. Trusting
 * the date flag prevents a stale strikethrough on an expired promotion.
 */
export function priceView(course: PriceSource): PriceView {
  const discounted =
    course.discountActive && course.effectivePrice < course.price && course.effectivePrice >= 0;
  const current = discounted ? course.effectivePrice : course.price;
  if (current <= 0) {
    return {
      kind: 'free',
      current: 0,
      original: null,
      percent: null,
      currency: course.currency,
    };
  }
  if (discounted) {
    return {
      kind: 'discount',
      current: course.effectivePrice,
      original: course.price,
      percent: Math.round((1 - course.effectivePrice / course.price) * 100),
      currency: course.currency,
    };
  }
  return {
    kind: 'paid',
    current: course.price,
    original: null,
    percent: null,
    currency: course.currency,
  };
}

export function priceLabel(course: PriceSource): string {
  return formatPrice(priceView(course).current, course.currency);
}
