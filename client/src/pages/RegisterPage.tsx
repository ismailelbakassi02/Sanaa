import { Button, Divider, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLock, IconUser } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { fetchMe, login, register } from '../api/auth';
import { messageForError } from '../api/messages';
import { AuthShell } from '../components/AuthShell';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useBranding } from '../tenant/BrandingContext';
import { useTenantSearch } from '../tenant/navigation';

interface RegisterFormValues {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export function RegisterPage() {
  const { slug, branding } = useBranding();
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();
  const status = useAuthStore((state) => state.status);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RegisterFormValues>();

  const handleSubmit = async () => {
    if (submitting) {
      return;
    }
    const values = form.getFieldsValue();
    if (!values.fullName || !values.email || !values.password || !values.confirmPassword) {
      return;
    }
    if (values.password !== values.confirmPassword) {
      Message.error(fr.auth.register.passwordMismatch);
      return;
    }

    setSubmitting(true);
    try {
      await register({
        tenantSlug: slug,
        email: values.email,
        password: values.password,
        fullName: values.fullName,
      });
      const { accessToken } = await login({
        tenantSlug: slug,
        email: values.email,
        password: values.password,
      });
      useAuthStore.getState().setAccessToken(accessToken);
      const user = await fetchMe();
      useAuthStore.getState().setSession(accessToken, user);
      Message.success(fr.auth.register.success);
      navigate(`/${tenantSearch}`, { replace: true });
    } catch (error) {
      Message.error(messageForError(error, 'register'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!branding.registrationEnabled) {
    return <Navigate to={`/login${tenantSearch}`} replace />;
  }

  if (status === 'authenticated') {
    return <Navigate to={`/${tenantSearch}`} replace />;
  }

  return (
    <AuthShell title={fr.auth.register.title} subtitle={fr.auth.register.subtitle}>
      <Form form={form} layout="vertical" size="large" onSubmit={handleSubmit}>
        <Form.Item
          label={fr.auth.register.nameLabel}
          field="fullName"
          rules={[{ required: true, message: fr.common.required }]}
        >
          <Input
            placeholder={fr.auth.register.namePlaceholder}
            autoComplete="name"
            prefix={
              <span aria-hidden="true">
                <IconUser />
              </span>
            }
          />
        </Form.Item>

        <Form.Item
          label={fr.auth.register.emailLabel}
          field="email"
          rules={[
            { required: true, message: fr.common.required },
            { type: 'email', message: fr.common.emailInvalid },
          ]}
        >
          <Input
            placeholder={fr.auth.register.emailPlaceholder}
            autoComplete="email"
            prefix={
              <span aria-hidden="true">
                <IconEmail />
              </span>
            }
          />
        </Form.Item>

        <Form.Item
          label={fr.auth.register.passwordLabel}
          field="password"
          rules={[
            { required: true, message: fr.common.required },
            { minLength: 10, message: fr.common.passwordMin },
          ]}
        >
          <Input.Password
            placeholder={fr.auth.register.passwordPlaceholder}
            autoComplete="new-password"
            prefix={
              <span aria-hidden="true">
                <IconLock />
              </span>
            }
          />
        </Form.Item>

        <Form.Item
          label={fr.auth.register.confirmLabel}
          field="confirmPassword"
          rules={[{ required: true, message: fr.common.required }]}
        >
          <Input.Password
            placeholder={fr.auth.register.confirmPlaceholder}
            autoComplete="new-password"
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
          {submitting ? fr.auth.register.submitting : fr.auth.register.submit}
        </Button>
      </Form>

      <Divider />
      <p className="sanaa-form-footer">
        <span>{fr.auth.register.haveAccount}</span>{' '}
        <Link className="sanaa-link" to={`/login${tenantSearch}`}>
          {fr.auth.register.signIn}
        </Link>
      </p>
    </AuthShell>
  );
}
