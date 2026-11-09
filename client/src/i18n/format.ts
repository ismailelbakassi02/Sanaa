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

export function formatPrice(amount: number, currency = 'MAD'): string {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency,
  }).format(amount);
}
