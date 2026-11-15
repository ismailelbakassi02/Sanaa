import { Button, Result } from '@arco-design/web-react';
import { Link } from 'react-router-dom';

import { fr } from '../i18n/fr';

export function NotFoundPage() {
  return (
    <div className="sanaa-center-page">
      <Result
        status="404"
        title={fr.notFound.title}
        subTitle={fr.notFound.body}
        extra={
          <Link to="/">
            <Button type="primary">{fr.notFound.backHome}</Button>
          </Link>
        }
      />
    </div>
  );
}
