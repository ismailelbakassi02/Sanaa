import { Button, Form, Input, Message } from '@arco-design/web-react';
import { IconLock } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { confirmGoogleLink, fetchMe } from '../api/auth';
import { errorDetail, toApiError } from '../api/errors';
import { messageForError } from '../api/messages';
import { AuthShell } from '../components/AuthShell';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { fr } from '../i18n/fr';
import { consumeGoogleRedirect } from '../lib/googleAuth';
import { useAuthStore } from '../store/auth';

interface GoogleLinkFormValues {
  password: string;
}

export function GoogleLinkPage() {
  useDocumentTitle(fr.auth.google.linkTitle);
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/me" replace />;
  }

  const handleSubmit = async (values: GoogleLinkFormValues) => {
    if (submitting) {
      return;
    }
    setSubmitting(true);
    try {
      const { accessToken } = await confirmGoogleLink({ password: values.password });
      useAuthStore.getState().setAccessToken(accessToken);
      const me = await fetchMe();
      useAuthStore.getState().setSession(accessToken, me);
      Message.success(fr.auth.google.linkSuccess);
      navigate(consumeGoogleRedirect(), { replace: true });
    } catch (error) {
      const statusCode = toApiError(error).status;
      const expired = statusCode === 404
        || (statusCode === 401 && errorDetail(error).includes('authentication required'));
      Message.error(
        expired ? fr.auth.google.linkExpired : messageForError(error, 'login'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell title={fr.auth.google.linkTitle} subtitle={fr.auth.google.linkSubtitle}>
      <Form<GoogleLinkFormValues> layout="vertical" size="large" onSubmit={handleSubmit}>
        <Form.Item
          label={fr.auth.google.linkPasswordLabel}
          field="password"
          rules={[{ required: true, message: fr.common.required }]}
        >
          <Input.Password
            placeholder={fr.auth.google.linkPasswordPlaceholder}
            autoComplete="current-password"
            prefix={
              <span aria-hidden="true">
                <IconLock />
              </span>
            }
          />
        </Form.Item>
        <Button type="primary" htmlType="submit" long loading={submitting}>
          {submitting ? fr.auth.google.linkSubmitting : fr.auth.google.linkSubmit}
        </Button>
      </Form>
      <p className="sanaa-form-footer">
        <Link className="sanaa-link" to="/login">
          {fr.auth.google.backToLogin}
        </Link>
      </p>
    </AuthShell>
  );
}
