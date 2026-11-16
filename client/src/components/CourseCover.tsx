import type { CSSProperties } from 'react';

import { coverFamily, initialsOf } from '../lib/cover';

interface CourseCoverProps {
  title: string;
  size?: 'card' | 'detail';
}

/**
 * Generated cover. The color family comes from the title hash and the shades
 * come from Arco tokens, so the same course keeps the same cover while light
 * and dark mode keep readable initials without any external image.
 */
export function CourseCover({ title, size = 'card' }: CourseCoverProps) {
  const family = coverFamily(title);
  const style = {
    '--sanaa-cover-from': `rgb(var(--${family}-1))`,
    '--sanaa-cover-to': `rgb(var(--${family}-3))`,
    '--sanaa-cover-ink': `rgb(var(--${family}-8))`,
  } as CSSProperties;

  return (
    <div
      className={`sanaa-cover sanaa-cover--${size}`}
      style={style}
      aria-hidden="true"
      data-cover-family={family}
    >
      <span className="sanaa-cover__initials">{initialsOf(title)}</span>
    </div>
  );
}
