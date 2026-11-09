import { Button, Divider, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLock } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { fetchMe, login } from '../api/auth';
import { messageForError } from '../api/messages';
import { AuthShell } from '../components/AuthShell';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useBranding } from '../tenant/BrandingContext';
import { useTenantSearch } from '../tenant/navigation';

interface LoginFormValues {
  email: string;
  password: string;
}

export function LoginPage() {
  const { slug, branding } = useBranding();
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();
  const status = useAuthStore((state) => state.status);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<LoginFormValues>();

  const handleSubmit = async () => {
    if (submitting) {
      return;
    }
    const values = form.getFieldsValue();
    if (!values.email || !values.password) {
      return;
    }

    setSubmitting(true);
    try {
      const { accessToken } = await login({
        tenantSlug: slug,
        email: values.email,
        password: values.password,
      });
      useAuthStore.getState().setAccessToken(accessToken);
      const user = await fetchMe();
      useAuthStore.getState().setSession(accessToken, user);
      navigate(`/${tenantSearch}`, { replace: true });
    } catch (error) {
      useAuthStore.getState().clear();
      Message.error(messageForError(error, 'login'));
    } finally {
      setSubmitting(false);
    }
  };

  if (status === 'authenticated') {
    return <Navigate to={`/${tenantSearch}`} replace />;
  }

  return (
    <AuthShell title={fr.auth.login.title} subtitle={fr.auth.login.subtitle}>
      <Form form={form} layout="vertical" size="large" onSubmit={handleSubmit}>
        <Form.Item
          label={fr.auth.login.emailLabel}
          field="email"
          rules={[
            { required: true, message: fr.common.required },
            { type: 'email', message: fr.common.emailInvalid },
          ]}
        >
          <Input
            placeholder={fr.auth.login.emailPlaceholder}
            autoComplete="email"
            prefix={
              <span aria-hidden="true">
                <IconEmail />
              </span>
            }
          />
        </Form.Item>

        <Form.Item
          label={fr.auth.login.passwordLabel}
          field="password"
          rules={[{ required: true, message: fr.common.required }]}
        >
          <Input.Password
            placeholder={fr.auth.login.passwordPlaceholder}
            autoComplete="current-password"
            prefix={
              <span aria-hidden="true">
                <IconLock />
              </span>
            }
          />
        </Form.Item>

        <Button
          type="primary"
          htmlType="submit"
          long
          loading={submitting}
          className="sanaa-submit"
        >
          {submitting ? fr.auth.login.submitting : fr.auth.login.submit}
        </Button>
      </Form>

      <Divider />
      {branding.registrationEnabled ? (
        <p className="sanaa-form-footer">
          <span>{fr.auth.login.noAccount}</span>{' '}
          <Link className="sanaa-link" to={`/register${tenantSearch}`}>
            {fr.auth.login.createAccount}
          </Link>
        </p>
      ) : (
        <p className="sanaa-form-footer sanaa-muted">{fr.auth.login.registrationDisabled}</p>
      )}
    </AuthShell>
  );
}
