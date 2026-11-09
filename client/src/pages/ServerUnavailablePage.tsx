import { Button, Result } from '@arco-design/web-react';

import { ThemeToggle } from '../components/ThemeToggle';
import { fr } from '../i18n/fr';

interface ServerUnavailablePageProps {
  onRetry: () => void;
}

/** Network failure, timeout or 5xx while loading the tenant branding. */
export function ServerUnavailablePage({ onRetry }: ServerUnavailablePageProps) {
  return (
    <div className="sanaa-center-page">
      <ThemeToggle floating />
      <Result
        status="warning"
        title={fr.brands.unavailableTitle}
        subTitle={fr.brands.unavailableBody}
        extra={
          <Button type="primary" onClick={onRetry}>
            {fr.common.retry}
          </Button>
        }
      />
    </div>
  );
}
