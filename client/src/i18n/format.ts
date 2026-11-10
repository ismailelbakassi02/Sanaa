import { fr } from './fr';

/**
 * Dates and money are always rendered through Intl so the French formatting
 * rules stay in one place.
 */
const dateTimeFormatter = new Intl.DateTimeFormat('fr-FR', {
  dateStyle: 'long',
  timeStyle: 'short',
});

export function formatDate(isoDate: string): string {
  return dateTimeFormatter.format(new Date(isoDate));
}

function moneyFormatter(currency: string): Intl.NumberFormat {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency,
  });
}

export function formatPrice(amount: number, currency = 'MAD'): string {
  if (amount === 0) {
    return fr.catalogue.free;
  }
  return moneyFormatter(currency).format(amount);
}

/**
 * Lesson duration as mm:ss. Minutes are not capped at 59: a 61-minute lesson
 * reads "61:00", which is clearer than inventing an hours segment.
 */
export function formatDuration(totalSeconds: number): string {
  const safe =
    Number.isFinite(totalSeconds) && totalSeconds > 0 ? Math.floor(totalSeconds) : 0;
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
