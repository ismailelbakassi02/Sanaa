import { Button, Divider } from '@arco-design/web-react';

import { useGoogleOAuthStatus } from '../hooks/auth';
import { fr } from '../i18n/fr';
import { rememberGoogleRedirect } from '../lib/googleAuth';

interface GoogleLoginButtonProps {
  from?: string | null;
}

/** Starts the server-side authorization-code flow; no Google token enters client storage. */
export function GoogleLoginButton({ from }: GoogleLoginButtonProps) {
  const status = useGoogleOAuthStatus();

  if (status.data?.enabled !== true) {
    return null;
  }

  return (
    <>
      <Divider>{fr.auth.google.or}</Divider>
      <Button
        long
        className="sanaa-google-button"
        onClick={() => {
          rememberGoogleRedirect(from);
          window.location.assign('/api/auth/oauth2/google');
        }}
      >
        {fr.auth.google.continue}
      </Button>
    </>
  );
}
