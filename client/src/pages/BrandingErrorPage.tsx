import { Button, Result } from '@arco-design/web-react';

import { ThemeToggle } from '../components/ThemeToggle';
import { fr } from '../i18n/fr';

interface BrandingErrorPageProps {
  onRetry: () => void;
}

/** Any branding failure that is neither a 404 nor a server/network problem. */
export function BrandingErrorPage({ onRetry }: BrandingErrorPageProps) {
  return (
    <div className="sanaa-center-page">
      <ThemeToggle floating />
      <Result
        status="warning"
        title={fr.brands.genericTitle}
        subTitle={fr.brands.genericBody}
        extra={
          <Button type="primary" onClick={onRetry}>
            {fr.common.retry}
          </Button>
        }
      />
    </div>
  );
}
