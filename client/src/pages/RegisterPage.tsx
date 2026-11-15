import { Button, Divider, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLock, IconUser } from '@arco-design/web-react/icon';
import { useState, type ReactNode } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { fetchMe, login, register } from '../api/auth';
import { messageForError } from '../api/messages';
import { AuthShell } from '../components/AuthShell';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { passwordIssue } from '../lib/password';
import { useAuthStore } from '../store/auth';

interface RegisterFormValues {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

function passwordValidator(value: unknown, callback: (error?: ReactNode) => void) {
  const text = typeof value === 'string' ? value : '';
  if (!text) {
    callback(fr.common.required);
    return;
  }
  const issue = passwordIssue(text);
  if (issue === 'tooShort') {
    callback(fr.common.passwordMin);
    return;
  }
  if (issue === 'tooLong') {
    callback(fr.common.passwordMax);
    return;
  }
  callback();
}

export function RegisterPage() {
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RegisterFormValues>();

  const handleSubmit = async (values: RegisterFormValues) => {
    if (submitting) {
      return;
    }
    if (values.password !== values.confirmPassword) {
      form.setFields({
        confirmPassword: { error: { message: fr.auth.register.passwordMismatch } },
      });
      return;
    }
    setSubmitting(true);
    try {
      await register({
        email: values.email.trim(),
        password: values.password,
        fullName: values.fullName.trim(),
      });
      const { accessToken } = await login({
        email: values.email.trim(),
        password: values.password,
      });
      useAuthStore.getState().setAccessToken(accessToken);
      const me = await fetchMe();
      useAuthStore.getState().setSession(accessToken, me);
      Message.success(fr.auth.register.success);
      navigate('/me', { replace: true });
    } catch (error) {
      if (!applyServerFieldErrors(form, error)) {
        Message.error(messageForError(error, 'register'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (status === 'authenticated') {
    return <Navigate to="/me" replace />;
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
          // The custom validator already rejects an empty value, but only the
          // required rule renders the asterisk and the required aria state.
          rules={[{ required: true, message: fr.common.required }, { validator: passwordValidator }]}
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

        <Button type="primary" htmlType="submit" long loading={submitting} className="sanaa-submit">
          {submitting ? fr.auth.register.submitting : fr.auth.register.submit}
        </Button>
      </Form>

      <Divider />
      <p className="sanaa-form-footer">
        <span>{fr.auth.register.haveAccount}</span>{' '}
        <Link className="sanaa-link" to="/login">
          {fr.auth.register.signIn}
        </Link>
      </p>
      <p className="sanaa-form-footer">
        <span>{fr.auth.register.creatorHint}</span>{' '}
        <Link className="sanaa-link" to="/become-creator">
          {fr.auth.register.becomeCreator}
        </Link>
      </p>
    </AuthShell>
  );
}
