import { Alert, Button, Card, Form, Input, Message } from '@arco-design/web-react';
import { IconLock } from '@arco-design/web-react/icon';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

import { changePassword, fetchMe, logout } from '../api/auth';
import { messageForError } from '../api/messages';
import { PageHeader } from '../components/PageHeader';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { passwordIssue } from '../lib/password';
import { useAuthStore } from '../store/auth';

interface PasswordFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

function newPasswordValidator(value: unknown, callback: (error?: ReactNode) => void) {
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

export function PasswordPage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const clear = useAuthStore((state) => state.clear);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<PasswordFormValues>();
  const forced = user?.mustChangePassword === true;

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // A revoked session is already logged out locally.
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  const handleSubmit = async (values: PasswordFormValues) => {
    if (submitting) {
      return;
    }
    if (values.newPassword !== values.confirmPassword) {
      form.setFields({ confirmPassword: { error: { message: fr.password.mismatch } } });
      return;
    }
    if (values.currentPassword === values.newPassword) {
      form.setFields({ newPassword: { error: { message: fr.password.same } } });
      return;
    }
    setSubmitting(true);
    try {
      const { accessToken } = await changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      useAuthStore.getState().setAccessToken(accessToken);
      const me = await fetchMe();
      useAuthStore.getState().setSession(accessToken, me);
      Message.success(fr.password.success);
      navigate('/me', { replace: true });
    } catch (error) {
      if (!applyServerFieldErrors(form, error)) {
        Message.error(messageForError(error, 'password'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sanaa-page sanaa-page--narrow">
      <PageHeader title={fr.password.title} subtitle={fr.password.subtitle} />

      {forced ? (
        <Alert
          type="warning"
          showIcon
          className="sanaa-alert"
          title={fr.password.forcedTitle}
          content={fr.password.forcedBody}
        />
      ) : null}

      <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
        <Form form={form} layout="vertical" onSubmit={handleSubmit}>
          <Form.Item
            label={fr.password.currentLabel}
            field="currentPassword"
            rules={[{ required: true, message: fr.common.required }]}
          >
            <Input.Password
              placeholder={fr.password.currentPlaceholder}
              autoComplete="current-password"
              prefix={
                <span aria-hidden="true">
                  <IconLock />
                </span>
              }
            />
          </Form.Item>

          <Form.Item
            label={fr.password.newLabel}
            field="newPassword"
            rules={[
              { required: true, message: fr.common.required },
              { validator: newPasswordValidator },
            ]}
          >
            <Input.Password
              placeholder={fr.password.newPlaceholder}
              autoComplete="new-password"
              prefix={
                <span aria-hidden="true">
                  <IconLock />
                </span>
              }
            />
          </Form.Item>

          <Form.Item
            label={fr.password.confirmLabel}
            field="confirmPassword"
            rules={[{ required: true, message: fr.common.required }]}
          >
            <Input.Password
              placeholder={fr.password.confirmPlaceholder}
              autoComplete="new-password"
              prefix={
                <span aria-hidden="true">
                  <IconLock />
                </span>
              }
            />
          </Form.Item>

          <div className="sanaa-form-actions">
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitting ? fr.password.submitting : fr.password.submit}
            </Button>
            {forced ? (
              <Button onClick={() => void handleLogout()}>{fr.password.logout}</Button>
            ) : null}
          </div>
        </Form>
      </Card>
    </div>
  );
}
