import { initialsOf } from '../lib/cover';

interface CourseCoverProps {
  title: string;
  size?: 'card' | 'detail';
}

/**
 * Generated cover: the gradient and its contrast come from the tenant
 * primary tokens, so no external image is fetched and dark mode stays coherent.
 * The block is decorative; the title is rendered as text next to it.
 */
export function CourseCover({ title, size = 'card' }: CourseCoverProps) {
  return (
    <div className={`sanaa-cover sanaa-cover--${size}`} aria-hidden="true">
      <span className="sanaa-cover__initials">{initialsOf(title)}</span>
    </div>
  );
}
