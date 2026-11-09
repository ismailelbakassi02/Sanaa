import { Button, Result } from '@arco-design/web-react';
import { useNavigate } from 'react-router-dom';

import { fr } from '../i18n/fr';
import { useTenantSearch } from '../tenant/navigation';

export function NotFoundPage() {
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();
  return (
    <div className="sanaa-center-page">
      <Result
        status="404"
        title={fr.notFound.title}
        subTitle={fr.notFound.body}
        extra={
          <Button type="primary" onClick={() => navigate(`/${tenantSearch}`)}>
            {fr.notFound.backHome}
          </Button>
        }
      />
    </div>
  );
}
