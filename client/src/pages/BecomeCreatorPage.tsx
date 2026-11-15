import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Message,
  Radio,
  Steps,
} from '@arco-design/web-react';
import { IconCheck, IconLeft } from '@arco-design/web-react/icon';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { fetchMe, login, restoreSession } from '../api/auth';
import { errorDetail } from '../api/errors';
import { messageForError } from '../api/messages';
import { createPublisher } from '../api/publishers';
import type { CreatePublisherPayload, PublisherType } from '../api/types';
import { BrandLogo } from '../components/BrandLogo';
import { ColorField } from '../components/ColorField';
import { PageHeader } from '../components/PageHeader';
import { useSlugAvailability } from '../hooks/publishers';
import { fr } from '../i18n/fr';
import { BRAND_COLOR } from '../lib/brand';
import { isHexColor } from '../lib/color';
import { applyServerFieldErrors } from '../lib/formErrors';
import { passwordIssue } from '../lib/password';
import { isValidSlug, normalizeSlug, slugFromName } from '../lib/slug';
import { useAuthStore } from '../store/auth';

interface OwnerFormValues {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

interface PublisherFormValues {
  publisherType: PublisherType;
  name: string;
  slug: string;
  primaryColor: string;
  contactEmail: string;
}

interface OwnerAccount {
  fullName: string;
  email: string;
  password: string;
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

export function BecomeCreatorPage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const [step, setStep] = useState(0);
  const [account, setAccount] = useState<OwnerAccount | null>(null);
  const [ownerForm] = Form.useForm<OwnerFormValues>();
  const [publisherForm] = Form.useForm<PublisherFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const slugCheck = useSlugAvailability(slug);

  // A platform administrator has no publisher and must not create one; a
  // member of a publisher already has a studio.
  const blocked = user !== null && (user.tenantSlug !== null || user.role !== 'STUDENT');

  const nextFromAccount = (values: OwnerFormValues) => {
    if (values.password !== values.confirmPassword) {
      ownerForm.setFields({
        confirmPassword: { error: { message: fr.becomeCreator.validation.confirm } },
      });
      return;
    }
    setAccount({
      fullName: values.fullName.trim(),
      email: values.email.trim(),
      password: values.password,
    });
    setStep(1);
  };

  const handlePublish = async (values: PublisherFormValues) => {
    if (submitting) {
      return;
    }
    const normalizedSlug = normalizeSlug(values.slug);
    if (!isValidSlug(normalizedSlug)) {
      publisherForm.setFields({
        slug: { error: { message: fr.becomeCreator.slugInvalid } },
      });
      return;
    }
    if (slugCheck.available === false) {
      publisherForm.setFields({
        slug: { error: { message: fr.becomeCreator.slugTaken } },
      });
      return;
    }
    if (!isHexColor(values.primaryColor)) {
      publisherForm.setFields({
        primaryColor: { error: { message: fr.becomeCreator.validation.color } },
      });
      return;
    }

    const payload: CreatePublisherPayload = {
      publisherType: values.publisherType,
      publisherName: values.name.trim(),
      slug: normalizedSlug,
      primaryColor: values.primaryColor.toUpperCase(),
      contactEmail: values.contactEmail?.trim() || undefined,
    };

    if (!user) {
      if (!account) {
        setStep(0);
        return;
      }
      payload.ownerFullName = account.fullName;
      payload.ownerEmail = account.email;
      payload.password = account.password;
    }

    setSubmitting(true);
    try {
      await createPublisher(payload);
      if (user) {
        // The access token still carries the learner claims. Refresh once so
        // the new TENANT_ADMIN role and tenant id reach every request.
        const token = await restoreSession();
        useAuthStore.getState().setAccessToken(token);
        useAuthStore.getState().setSession(token, await fetchMe());
      } else if (account) {
        const { accessToken } = await login({
          email: account.email,
          password: account.password,
        });
        useAuthStore.getState().setAccessToken(accessToken);
        useAuthStore.getState().setSession(accessToken, await fetchMe());
      }
      Message.success(user ? fr.becomeCreator.successStudio : fr.becomeCreator.successAnonymous);
      navigate('/studio/profile', { replace: true });
    } catch (error) {
      const detail = errorDetail(error);
      if (detail.includes('slug is already taken')) {
        publisherForm.setFields({
          slug: { error: { message: fr.becomeCreator.slugTaken } },
        });
        setStep(1);
      } else if (detail.includes('email is already registered')) {
        if (user) {
          Message.error(fr.errors.emailAlreadyRegistered);
        } else {
          setStep(0);
          ownerForm.setFields({
            email: { error: { message: fr.errors.emailAlreadyRegistered } },
          });
        }
      } else if (
        detail.includes('owner name, email and password are required') ||
        detail.includes('owner fields must not be sent')
      ) {
        Message.error(fr.errors.invalidData);
      } else if (!applyServerFieldErrors(publisherForm, error)) {
        Message.error(messageForError(error, 'create-publisher'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (blocked && user) {
    const admin = user.role === 'SUPER_ADMIN';
    return (
      <main className="sanaa-page sanaa-page--narrow">
        <PageHeader title={fr.becomeCreator.title} subtitle={fr.becomeCreator.subtitle} />
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 32 }}>
          <div className="sanaa-conflict">
            <BrandLogo variant="mark" height={40} />
            <h2 className="sanaa-title-lg">
              {admin ? fr.becomeCreator.accountAdminTitle : fr.becomeCreator.accountConflictTitle}
            </h2>
            <p className="sanaa-text sanaa-muted">
              {admin ? fr.becomeCreator.accountAdminBody : fr.becomeCreator.accountConflictBody}
            </p>
            <Link to={admin ? '/me' : '/studio/profile'}>
              <Button type="primary">
                {admin
                  ? fr.becomeCreator.accountAdminAction
                  : fr.becomeCreator.accountConflictAction}
              </Button>
            </Link>
          </div>
        </Card>
      </main>
    );
  }

  return (
    <main className="sanaa-page sanaa-page--narrow">
      <PageHeader title={fr.becomeCreator.title} subtitle={fr.becomeCreator.subtitle} />

      <Steps current={step + 1} className="sanaa-steps">
        <Steps.Step title={fr.becomeCreator.stepAccount} />
        <Steps.Step title={fr.becomeCreator.stepPublisher} />
      </Steps>

      {step === 0 ? (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
          <h2 className="sanaa-title-lg">{fr.becomeCreator.accountTitle}</h2>
          <p className="sanaa-text sanaa-muted">{fr.becomeCreator.accountBody}</p>

          {user ? (
            <Alert
              type="info"
              showIcon
              className="sanaa-alert"
              content={fr.becomeCreator.authenticatedAs(user.fullName, user.email)}
            />
          ) : (
            <Form
              form={ownerForm}
              layout="vertical"
              onSubmit={nextFromAccount}
              initialValues={account ?? undefined}
            >
              <h3 className="sanaa-title-md">{fr.becomeCreator.ownerTitle}</h3>
              <p className="sanaa-caption">{fr.becomeCreator.ownerBody}</p>
              <Form.Item
                label={fr.becomeCreator.fullNameLabel}
                field="fullName"
                rules={[
                  { required: true, message: fr.common.required },
                  { minLength: 2, message: fr.becomeCreator.validation.ownerName },
                  { maxLength: 120, message: fr.becomeCreator.validation.ownerName },
                ]}
              >
                <Input placeholder={fr.becomeCreator.fullNamePlaceholder} autoComplete="name" />
              </Form.Item>
              <Form.Item
                label={fr.becomeCreator.emailLabel}
                field="email"
                rules={[
                  { required: true, message: fr.common.required },
                  { type: 'email', message: fr.becomeCreator.validation.contact },
                ]}
              >
                <Input placeholder={fr.becomeCreator.emailPlaceholder} autoComplete="email" />
              </Form.Item>
              <Form.Item
                label={fr.becomeCreator.passwordLabel}
                field="password"
                rules={[
                  { required: true, message: fr.common.required },
                  { validator: passwordValidator },
                ]}
              >
                <Input.Password
                  placeholder={fr.becomeCreator.passwordPlaceholder}
                  autoComplete="new-password"
                />
              </Form.Item>
              <Form.Item
                label={fr.becomeCreator.confirmLabel}
                field="confirmPassword"
                rules={[{ required: true, message: fr.common.required }]}
              >
                <Input.Password
                  placeholder={fr.becomeCreator.confirmPlaceholder}
                  autoComplete="new-password"
                />
              </Form.Item>
              <Button type="primary" htmlType="submit">
                {fr.becomeCreator.continue}
              </Button>
            </Form>
          )}

          {user ? (
            <div className="sanaa-form-actions">
              <Button type="primary" onClick={() => setStep(1)}>
                {fr.becomeCreator.continue}
              </Button>
            </div>
          ) : null}
        </Card>
      ) : (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
          <h2 className="sanaa-title-lg">{fr.becomeCreator.publisherTitle}</h2>
          <p className="sanaa-text sanaa-muted">{fr.becomeCreator.publisherBody}</p>

          <Form
            form={publisherForm}
            layout="vertical"
            onSubmit={handlePublish}
            initialValues={{
              publisherType: 'INDIVIDUAL',
              primaryColor: BRAND_COLOR,
              slug,
            }}
            onValuesChange={(changed: Partial<PublisherFormValues>) => {
              if ('name' in changed) {
                const next = slugFromName(String(changed.name ?? ''));
                if (!slugTouched) {
                  setSlug(next);
                  publisherForm.setFieldValue('slug', next);
                }
              }
              if ('slug' in changed) {
                setSlugTouched(true);
                setSlug(String(changed.slug ?? ''));
              }
            }}
          >
            <Form.Item
              label={fr.becomeCreator.typeLabel}
              field="publisherType"
              rules={[{ required: true, message: fr.common.required }]}
            >
              <Radio.Group className="sanaa-type-choice">
                <Radio value="INDIVIDUAL">
                  <span className="sanaa-type-choice__title">{fr.becomeCreator.typeIndividual}</span>
                  <span className="sanaa-type-choice__hint">
                    {fr.becomeCreator.typeIndividualHint}
                  </span>
                </Radio>
                <Radio value="ORGANIZATION">
                  <span className="sanaa-type-choice__title">
                    {fr.becomeCreator.typeOrganization}
                  </span>
                  <span className="sanaa-type-choice__hint">
                    {fr.becomeCreator.typeOrganizationHint}
                  </span>
                </Radio>
              </Radio.Group>
            </Form.Item>

            <Form.Item
              label={fr.becomeCreator.nameLabel}
              field="name"
              rules={[
                { required: true, message: fr.common.required },
                { minLength: 2, message: fr.becomeCreator.validation.name },
                { maxLength: 120, message: fr.becomeCreator.validation.name },
              ]}
            >
              <Input placeholder={fr.becomeCreator.namePlaceholder} />
            </Form.Item>

            <Form.Item
              label={fr.becomeCreator.slugLabel}
              field="slug"
              extra={
                slugCheck.isChecking ? (
                  fr.becomeCreator.slugChecking
                ) : slugCheck.available === true ? (
                  <span className="sanaa-field-ok">
                    <IconCheck aria-hidden="true" /> {fr.becomeCreator.slugAvailable}
                  </span>
                ) : slugCheck.available === false ? (
                  <span className="sanaa-field-error">{fr.becomeCreator.slugTaken}</span>
                ) : (
                  fr.becomeCreator.slugHint
                )
              }
              rules={[
                { required: true, message: fr.common.required },
                {
                  match: /^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$/,
                  message: fr.becomeCreator.slugInvalid,
                },
              ]}
            >
              <Input
                addBefore={fr.becomeCreator.slugPrefix}
                placeholder="mon-studio"
                autoComplete="off"
                spellCheck={false}
              />
            </Form.Item>

            <Form.Item
              label={fr.becomeCreator.colorLabel}
              field="primaryColor"
              rules={[{ required: true, message: fr.becomeCreator.validation.color }]}
              extra={fr.becomeCreator.colorHint}
            >
              <ColorField />
            </Form.Item>

            <Form.Item
              label={fr.becomeCreator.contactLabel}
              field="contactEmail"
              rules={[
                { required: true, message: fr.common.required },
                { type: 'email', message: fr.becomeCreator.validation.contact },
              ]}
              extra={fr.becomeCreator.contactHint}
            >
              <Input placeholder={fr.becomeCreator.emailPlaceholder} autoComplete="email" />
            </Form.Item>

            <div className="sanaa-form-actions">
              <Button
                htmlType="button"
                icon={<IconLeft />}
                onClick={() => setStep(0)}
                disabled={submitting}
              >
                {fr.becomeCreator.back}
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={submitting}
                disabled={slugCheck.isChecking}
              >
                {submitting ? fr.becomeCreator.submitting : fr.becomeCreator.submit}
              </Button>
            </div>
          </Form>
        </Card>
      )}
    </main>
  );
}
