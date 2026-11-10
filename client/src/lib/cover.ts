/**
 * Decorative cover generated from the course title. The gradient itself uses
 * the tenant primary tokens in CSS, so the cover follows branding and dark mode
 * without any external image.
 */
export function initialsOf(title: string): string {
  const words = title.trim().split(/\s+/).filter((word) => word.length > 0);
  const letters = words
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? '')
    .join('');
  return letters.toUpperCase() || '?';
}
