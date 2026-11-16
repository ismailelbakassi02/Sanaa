import { Alert, Button, Card, Descriptions, Form, Grid, Input, Message, Tag } from '@arco-design/web-react';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { messageForError } from '../api/messages';
import { PageHeader } from '../components/PageHeader';
import { ImageUploadField } from '../components/ImageUploadField';
import { LinkFields } from '../components/LinkFields';
import { useMyProfile, useUpdateMyProfile, profileKeys } from '../hooks/profile';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { useAuthStore } from '../store/auth';

interface ProfileFormValues {
  fullName: string;
  headline?: string;
  bio?: string;
  websiteUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  youtubeUrl?: string;
}

function blankToEmpty(value: string | undefined): string {
  return value?.trim() ?? '';
}

export function MePage() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const query = useMyProfile();
  const mutation = useUpdateMyProfile();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ProfileFormValues>();
  const [avatarImageId, setAvatarImageId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!query.data) {
      return;
    }
    setAvatarImageId(query.data.avatarImageId);
    form.setFieldsValue({
      fullName: query.data.fullName,
      headline: query.data.headline ?? '',
      bio: query.data.bio ?? '',
      websiteUrl: query.data.websiteUrl ?? '',
      instagramUrl: query.data.instagramUrl ?? '',
      linkedinUrl: query.data.linkedinUrl ?? '',
      youtubeUrl: query.data.youtubeUrl ?? '',
    });
  }, [query.data, form]);

  if (!user) {
    return null;
  }

  const handleSubmit = async (values: ProfileFormValues) => {
    setError(null);
    try {
      const updated = await mutation.mutateAsync({
        fullName: values.fullName.trim(),
        headline: blankToEmpty(values.headline),
        bio: blankToEmpty(values.bio),
        websiteUrl: blankToEmpty(values.websiteUrl),
        instagramUrl: blankToEmpty(values.instagramUrl),
        linkedinUrl: blankToEmpty(values.linkedinUrl),
        youtubeUrl: blankToEmpty(values.youtubeUrl),
      });
      setUser({ ...user, fullName: updated.fullName });
      Message.success(fr.me.saved);
    } catch (mutationError) {
      if (!applyServerFieldErrors(form, mutationError)) {
        setError(messageForError(mutationError, 'profile'));
      }
    }
  };

  const publisherLink =
    user.tenantSlug !== null ? `/publishers/${encodeURIComponent(user.tenantSlug)}` : null;
  const canManageStudio = user.role === 'TENANT_ADMIN' || user.role === 'INSTRUCTOR';

  return (
    <div className="sanaa-page">
      <PageHeader title={fr.me.title} subtitle={fr.me.subtitle} />

      {error ? (
        <Alert type="error" showIcon closable content={error} onClose={() => setError(null)} />
      ) : null}

      <Grid.Row gutter={[24, 24]}>
        <Grid.Col xs={24} lg={8}>
          <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
            <h2 className="sanaa-title-lg">{fr.me.identityTitle}</h2>
            <Descriptions
              column={1}
              data={[
                { key: 'name', label: fr.me.nameLabel, value: user.fullName },
                { key: 'email', label: fr.me.emailLabel, value: user.email },
                {
                  key: 'role',
                  label: fr.me.roleLabel,
                  value: <Tag>{fr.roles[user.role]}</Tag>,
                },
                {
                  key: 'publisher',
                  label: fr.me.publisherLabel,
                  value: user.tenantSlug ? (
                    <Tag color="arcoblue">{user.tenantSlug}</Tag>
                  ) : (
                    fr.me.noPublisher
                  ),
                },
              ]}
            />

            <div className="sanaa-link-list">
              {publisherLink ? (
                <Link to={publisherLink} className="sanaa-link">
                  {fr.me.publicPage}
                </Link>
              ) : null}
              {canManageStudio ? (
                <Link to="/studio/profile" className="sanaa-link">
                  {fr.me.studio}
                </Link>
              ) : null}
              {user.role === 'TENANT_ADMIN' && user.tenantType === 'ORGANIZATION' ? (
                <Link to="/studio/team" className="sanaa-link">
                  {fr.me.team}
                </Link>
              ) : null}
              {user.role === 'SUPER_ADMIN' ? (
                <Link to="/admin" className="sanaa-link">
                  {fr.me.administration}
                </Link>
              ) : null}
              <Link to="/password" className="sanaa-link">
                {user.hasPassword ? fr.nav.password : fr.password.setTitle}
              </Link>
            </div>

            {user.role === 'STUDENT' && !user.tenantSlug ? (
              <div className="sanaa-inline-cta">
                <h3 className="sanaa-title-md">{fr.me.becomeCreatorTitle}</h3>
                <p className="sanaa-text sanaa-muted">{fr.me.becomeCreatorBody}</p>
                <Link to="/become-creator">
                  <Button type="primary">{fr.nav.becomeCreator}</Button>
                </Link>
              </div>
            ) : null}
          </Card>
        </Grid.Col>

        <Grid.Col xs={24} lg={16}>
          <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
            <h2 className="sanaa-title-lg">{fr.me.profileTitle}</h2>
            <p className="sanaa-text sanaa-muted">{fr.me.profileLead}</p>

            {query.isError ? (
              <Alert type="error" showIcon content={fr.me.loadError} />
            ) : (
              <Form
                form={form}
                layout="vertical"
                onSubmit={handleSubmit}
                disabled={mutation.isPending}
                className={query.isPending ? 'sanaa-form--loading' : undefined}
              >
                <div className="sanaa-field">
                  <span className="sanaa-field__label">{fr.me.avatarLabel}</span>
                  <ImageUploadField
                    kind="avatar"
                    value={avatarImageId}
                    onChange={(id) => {
                      setAvatarImageId(id);
                      void queryClient.invalidateQueries({ queryKey: profileKeys.me });
                    }}
                  />
                  <p className="sanaa-caption">{fr.me.avatarHint}</p>
                </div>

                <Form.Item
                  label={fr.me.fullNameLabel}
                  field="fullName"
                  rules={[
                    { required: true, message: fr.common.required },
                    { minLength: 2, message: fr.validation.name },
                    { maxLength: 120, message: fr.validation.name },
                  ]}
                >
                  <Input autoComplete="name" />
                </Form.Item>

                <Form.Item
                  label={fr.me.headlineLabel}
                  field="headline"
                  rules={[{ maxLength: 120, message: fr.validation.tooLong(120) }]}
                >
                  <Input placeholder={fr.me.headlinePlaceholder} />
                </Form.Item>

                <Form.Item
                  label={fr.me.bioLabel}
                  field="bio"
                  rules={[{ maxLength: 1000, message: fr.validation.tooLong(1000) }]}
                >
                  <Input.TextArea
                    placeholder={fr.me.bioPlaceholder}
                    autoSize={{ minRows: 4, maxRows: 10 }}
                  />
                </Form.Item>

                <LinkFields />
                <Button type="primary" htmlType="submit" loading={mutation.isPending}>
                  {mutation.isPending ? fr.common.saving : fr.common.save}
                </Button>
              </Form>
            )}
          </Card>
        </Grid.Col>
      </Grid.Row>
    </div>
  );
}
