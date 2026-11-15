import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Message,
  Result,
  Skeleton,
} from '@arco-design/web-react';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { messageForError } from '../api/messages';
import { ColorField } from '../components/ColorField';
import { ImageUploadField } from '../components/ImageUploadField';
import { LinkFields } from '../components/LinkFields';
import { PageHeader } from '../components/PageHeader';
import { publisherKeys, usePublicPublisher, useUpdatePublisher } from '../hooks/publishers';
import { fr } from '../i18n/fr';
import { BRAND_COLOR } from '../lib/brand';
import { isHexColor } from '../lib/color';
import { applyServerFieldErrors } from '../lib/formErrors';
import { useAuthStore } from '../store/auth';

interface StudioFormValues {
  name: string;
  headline?: string;
  description?: string;
  websiteUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  youtubeUrl?: string;
  primaryColor: string;
  contactEmail: string;
}

function blankToEmpty(value: string | undefined): string {
  return value?.trim() ?? '';
}

export function StudioProfilePage() {
  const user = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();
  const slug = user?.tenantSlug ?? null;
  const query = usePublicPublisher(slug);
  const mutation = useUpdatePublisher();
  const [form] = Form.useForm<StudioFormValues>();
  const [logoImageId, setLogoImageId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const readOnly = user?.role !== 'TENANT_ADMIN';

  useEffect(() => {
    if (!query.data) {
      return;
    }
    setLogoImageId(query.data.logoImageId);
    form.setFieldsValue({
      name: query.data.name,
      headline: query.data.headline ?? '',
      description: query.data.description ?? '',
      websiteUrl: query.data.websiteUrl ?? '',
      instagramUrl: query.data.instagramUrl ?? '',
      linkedinUrl: query.data.linkedinUrl ?? '',
      youtubeUrl: query.data.youtubeUrl ?? '',
      primaryColor: query.data.primaryColor ?? BRAND_COLOR,
      contactEmail: '',
    });
  }, [query.data, form]);

  if (!slug) {
    return (
      <div className="sanaa-page sanaa-page--narrow">
        <Result
          status="warning"
          title={fr.studio.profile.notFound}
          extra={
            <Link to="/me">
              <Button type="primary">{fr.common.backHome}</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const handleSubmit = async (values: StudioFormValues) => {
    setError(null);
    if (!isHexColor(values.primaryColor)) {
      form.setFields({ primaryColor: { error: { message: fr.validation.color } } });
      return;
    }
    try {
      await mutation.mutateAsync({
        name: values.name.trim(),
        headline: blankToEmpty(values.headline),
        description: blankToEmpty(values.description),
        websiteUrl: blankToEmpty(values.websiteUrl),
        instagramUrl: blankToEmpty(values.instagramUrl),
        linkedinUrl: blankToEmpty(values.linkedinUrl),
        youtubeUrl: blankToEmpty(values.youtubeUrl),
        primaryColor: values.primaryColor.toUpperCase(),
        contactEmail: values.contactEmail.trim(),
      });
      Message.success(fr.studio.profile.saved);
      void queryClient.invalidateQueries({ queryKey: publisherKeys.public(slug) });
    } catch (mutationError) {
      if (!applyServerFieldErrors(form, mutationError)) {
        setError(messageForError(mutationError, 'profile'));
      }
    }
  };

  return (
    <div className="sanaa-page">
      <PageHeader
        title={fr.studio.profile.title}
        subtitle={fr.studio.profile.subtitle}
        actions={
          <Link to={`/publishers/${encodeURIComponent(slug)}`}>
            <Button>{fr.studio.profile.publicPage}</Button>
          </Link>
        }
      />

      {readOnly ? (
        <Alert
          type="info"
          showIcon
          className="sanaa-alert"
          title={fr.studio.profile.readOnlyTitle}
          content={fr.studio.profile.readOnlyBody}
        />
      ) : null}

      {error ? (
        <Alert type="error" showIcon closable content={error} onClose={() => setError(null)} />
      ) : null}

      {query.isPending ? (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
          <Skeleton animation text={{ rows: 8 }} />
        </Card>
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.studio.profile.loadError}
          subTitle={fr.studio.profile.notFound}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
          <Form
            form={form}
            layout="vertical"
            onSubmit={handleSubmit}
            disabled={readOnly || mutation.isPending}
            className="sanaa-form--narrow"
          >
            <div className="sanaa-field">
              <span className="sanaa-field__label">{fr.studio.profile.logoLabel}</span>
              <ImageUploadField
                kind="logo"
                value={logoImageId}
                disabled={readOnly}
                onChange={(id) => {
                  setLogoImageId(id);
                  void queryClient.invalidateQueries({ queryKey: publisherKeys.public(slug) });
                }}
              />
              <p className="sanaa-caption">{fr.studio.profile.logoHint}</p>
            </div>

            <Form.Item
              label={fr.studio.profile.nameLabel}
              field="name"
              rules={[
                { required: true, message: fr.common.required },
                { minLength: 2, message: fr.validation.name },
                { maxLength: 120, message: fr.validation.name },
              ]}
            >
              <Input />
            </Form.Item>

            <Form.Item
              label={fr.studio.profile.headlineLabel}
              field="headline"
              rules={[{ maxLength: 160, message: fr.validation.tooLong(160) }]}
            >
              <Input placeholder={fr.studio.profile.headlinePlaceholder} />
            </Form.Item>

            <Form.Item
              label={fr.studio.profile.descriptionLabel}
              field="description"
              rules={[{ maxLength: 2000, message: fr.validation.tooLong(2000) }]}
            >
              <Input.TextArea
                placeholder={fr.studio.profile.descriptionPlaceholder}
                autoSize={{ minRows: 5, maxRows: 12 }}
              />
            </Form.Item>

            <h3 className="sanaa-title-md">{fr.studio.profile.linksTitle}</h3>
            <LinkFields disabled={readOnly} />

            <Form.Item
              label={fr.studio.profile.colorLabel}
              field="primaryColor"
              rules={[{ required: true, message: fr.validation.color }]}
              extra={fr.studio.profile.colorHint}
            >
              <ColorField disabled={readOnly} />
            </Form.Item>

            {!readOnly ? (
              <Alert
                type="warning"
                showIcon
                className="sanaa-alert"
                content={fr.studio.profile.contactNotice}
              />
            ) : null}

            <Form.Item
              label={fr.studio.profile.contactLabel}
              field="contactEmail"
              rules={[
                { required: !readOnly, message: fr.common.required },
                { type: 'email', message: fr.validation.email },
              ]}
              extra={fr.studio.profile.contactHint}
            >
              <Input autoComplete="email" />
            </Form.Item>

            {!readOnly ? (
              <Button type="primary" htmlType="submit" loading={mutation.isPending}>
                {mutation.isPending ? fr.common.saving : fr.studio.profile.save}
              </Button>
            ) : null}
          </Form>
        </Card>
      )}
    </div>
  );
}
