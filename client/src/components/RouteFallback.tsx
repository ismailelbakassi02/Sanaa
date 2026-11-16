import { Skeleton } from '@arco-design/web-react';

import { fr } from '../i18n/fr';

/**
 * The Suspense fallback shown while a page chunk downloads. It uses the same
 * card skeleton as the pages so the layout does not jump, and it announces the
 * French loading text to assistive technology.
 */
export function RouteFallback() {
  return (
    <div className="sanaa-page" role="status" aria-live="polite">
      <span className="sanaa-sr-only">{fr.common.loading}</span>
      <Skeleton animation text={{ rows: 8 }} />
    </div>
  );
}
