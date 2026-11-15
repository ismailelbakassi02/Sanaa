import { FULL_LOGO_PATH, FULL_LOGO_VIEWBOX, MARK_PATH, MARK_VIEWBOX } from './brandPaths';

export type BrandLogoVariant = 'full' | 'mark';

interface BrandLogoProps {
  /** `full` is the lockup with the wordmark; `mark` is the symbol only. */
  variant?: BrandLogoVariant;
  /** Rendered height in pixels. The width follows the viewBox ratio. */
  height?: number;
  className?: string;
}

/**
 * Inline SANAA logo. The path data is generated from docs/brand and embedded,
 * so the logo renders before any network request and inherits `currentColor`
 * in both themes. The accessible name is always "SANAA".
 */
export function BrandLogo({ variant = 'mark', height = 32, className }: BrandLogoProps) {
  const full = variant === 'full';
  const classes = ['sanaa-logo', full ? 'sanaa-logo--full' : 'sanaa-logo--mark', className]
    .filter(Boolean)
    .join(' ');

  return (
    <svg
      className={classes}
      viewBox={full ? FULL_LOGO_VIEWBOX : MARK_VIEWBOX}
      height={height}
      role="img"
      aria-label="SANAA"
      focusable="false"
      preserveAspectRatio="xMidYMid meet"
    >
      <title>SANAA</title>
      <path
        d={full ? FULL_LOGO_PATH : MARK_PATH}
        fill="currentColor"
        fillRule="evenodd"
      />
    </svg>
  );
}
