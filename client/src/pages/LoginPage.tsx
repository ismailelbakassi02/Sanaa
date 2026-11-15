import { Button, Divider, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLock } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';

import { fetchMe, login } from '../api/auth';
import { messageForError } from '../api/messages';
import type { MeResponse } from '../api/types';
import { AuthShell } from '../components/AuthShell';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { useAuthStore } from '../store/auth';

interface LoginFormValues {
  email: string;
  password: string;
}

function landingPath(user: MeResponse): string {
  return user.mustChangePassword ? '/password' : '/me';
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<LoginFormValues>();

  const from = (location.state as { from?: string } | null)?.from;

  const handleSubmit = async (values: LoginFormValues) => {
    if (submitting) {
      return;
    }
    setSubmitting(true);
    try {
      const { accessToken } = await login({
        email: values.email.trim(),
        password: values.password,
      });
      useAuthStore.getState().setAccessToken(accessToken);
      const me = await fetchMe();
      useAuthStore.getState().setSession(accessToken, me);
      Message.success(fr.auth.login.success);
      navigate(me.mustChangePassword ? '/password' : (from ?? landingPath(me)), {
        replace: true,
      });
    } catch (error) {
      useAuthStore.getState().clear();
      if (!applyServerFieldErrors(form, error)) {
        Message.error(messageForError(error, 'login'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (status === 'authenticated' && user) {
    return <Navigate to={landingPath(user)} replace />;
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

        <Button type="primary" htmlType="submit" long loading={submitting} className="sanaa-submit">
          {submitting ? fr.auth.login.submitting : fr.auth.login.submit}
        </Button>
      </Form>

      <Divider />
      <p className="sanaa-form-footer">
        <span>{fr.auth.login.noAccount}</span>{' '}
        <Link className="sanaa-link" to="/register">
          {fr.auth.login.createAccount}
        </Link>
      </p>
      <p className="sanaa-form-footer">
        <span>{fr.auth.login.creatorQuestion}</span>{' '}
        <Link className="sanaa-link" to="/become-creator">
          {fr.auth.login.becomeCreator}
        </Link>
      </p>
    </AuthShell>
  );
}
