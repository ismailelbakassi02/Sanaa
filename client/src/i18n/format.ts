import { fr } from './fr';

/**
 * Dates and money are always rendered through Intl so the French formatting
 * rules stay in one place.
 */
const dateTimeFormatter = new Intl.DateTimeFormat('fr-FR', {
  dateStyle: 'long',
  timeStyle: 'short',
});

const dateTimeShortFormatter = new Intl.DateTimeFormat('fr-FR', {
  dateStyle: 'short',
  timeStyle: 'short',
});

export function formatDate(isoDate: string): string {
  return dateTimeFormatter.format(new Date(isoDate));
}

/** Dense-table variant: 13/11/2026 01:13 stays on one line in a narrow column. */
export function formatDateTimeShort(isoDate: string): string {
  return dateTimeShortFormatter.format(new Date(isoDate));
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

const fileSizeFormatter = new Intl.NumberFormat('fr-FR', {
  maximumFractionDigits: 1,
});

/** Receipt size in French units: octets, Ko and Mo. */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return `0 o`;
  }
  if (bytes < 1024) {
    return `${bytes} o`;
  }
  const kilobytes = bytes / 1024;
  if (kilobytes < 1024) {
    return `${fileSizeFormatter.format(kilobytes)} Ko`;
  }
  return `${fileSizeFormatter.format(kilobytes / 1024)} Mo`;
}
