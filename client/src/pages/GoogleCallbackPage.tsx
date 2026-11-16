import { Button, Result } from '@arco-design/web-react';
import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';

import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { fr } from '../i18n/fr';
import { consumeGoogleRedirect } from '../lib/googleAuth';
import { useAuthStore } from '../store/auth';

/** The refresh cookie was set by auth-service; Root restores it before this route renders. */
export function GoogleCallbackPage() {
  useDocumentTitle(fr.auth.google.callbackTitle);
  const status = useAuthStore((state) => state.status);
  const [destination] = useState(consumeGoogleRedirect);

  if (status === 'authenticated') {
    return <Navigate to={destination} replace />;
  }

  return (
    <main className="sanaa-center-page">
      <Result
        status="warning"
        title={<h1 className="sanaa-result-title">{fr.auth.google.callbackErrorTitle}</h1>}
        subTitle={fr.auth.google.callbackErrorBody}
        extra={
          <Link to="/login">
            <Button type="primary">{fr.auth.google.backToLogin}</Button>
          </Link>
        }
      />
    </main>
  );
}
