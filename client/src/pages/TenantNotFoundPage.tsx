import { Button, Result } from '@arco-design/web-react';

import { fr } from '../i18n/fr';
import { ThemeToggle } from '../components/ThemeToggle';

interface TenantNotFoundPageProps {
  slug: string;
  onChooseTenant: () => void;
}

export function TenantNotFoundPage({ slug, onChooseTenant }: TenantNotFoundPageProps) {
  return (
    <div className="sanaa-center-page">
      <ThemeToggle floating />
      <Result
        status="404"
        title={fr.brands.notFoundTitle}
        subTitle={fr.brands.notFoundBody(slug)}
        extra={
          <Button type="primary" onClick={onChooseTenant}>
            {fr.brands.chooseTenant}
          </Button>
        }
      />
    </div>
  );
}
