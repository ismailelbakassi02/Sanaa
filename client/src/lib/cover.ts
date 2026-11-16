/**
 * Decorative cover generated from the course title. The color family is chosen
 * deterministically from the title, so a course keeps the same cover on every
 * page and between reloads, without any server-side image.
 */
const COVER_FAMILIES = [
  'arcoblue',
  'purple',
  'magenta',
  'red',
  'orange',
  'gold',
  'green',
  'cyan',
] as const;

export type CoverFamily = (typeof COVER_FAMILIES)[number];

export function initialsOf(title: string): string {
  const words = title.trim().split(/\s+/).filter((word) => word.length > 0);
  const letters = words
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? '')
    .join('');
  return letters.toUpperCase() || '?';
}

/** Stable, locale-independent hash. A tiny change to the title changes the hue. */
function hashTitle(title: string): number {
  let hash = 0;
  for (const character of Array.from(title.trim().toLowerCase())) {
    hash = (hash * 31 + (character.codePointAt(0) ?? 0)) >>> 0;
  }
  return hash;
}

export function coverFamily(title: string): CoverFamily {
  return COVER_FAMILIES[hashTitle(title) % COVER_FAMILIES.length];
}
