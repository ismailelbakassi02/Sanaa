import {
  Button,
  Card,
  Descriptions,
  Grid,
  Result,
  Skeleton,
  Tag,
  Typography,
} from '@arco-design/web-react';
import {
  IconApps,
  IconBook,
  IconEdit,
  IconSafe,
} from '@arco-design/web-react/icon';
import { useQuery } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';

import { fetchMe } from '../api/auth';
import type { Role } from '../api/types';
import { formatDate } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useBranding } from '../tenant/BrandingContext';

const CARD_STYLE = { borderRadius: 8 };
const CARD_BODY_STYLE = { padding: 24 };

function roleIcon(role: Role): ReactNode {
  switch (role) {
    case 'STUDENT':
      return <IconBook />;
    case 'INSTRUCTOR':
      return <IconEdit />;
    case 'TENANT_ADMIN':
      return <IconSafe />;
    case 'SUPER_ADMIN':
      return <IconApps />;
  }
}

export function HomePage() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const sessionStartedAt = useAuthStore((state) => state.sessionStartedAt);
  const { branding } = useBranding();

  const query = useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    placeholderData: user ?? undefined,
    retry: 1,
  });

  useEffect(() => {
    if (query.data) {
      setUser(query.data);
    }
  }, [query.data, setUser]);

  if (!user) {
    return null;
  }

  if (query.isPending) {
    return (
      <div className="sanaa-page">
        <div className="sanaa-page__head">
          <Skeleton animation text={{ rows: 1, width: [240] }} />
        </div>
        <Grid.Row gutter={[24, 24]}>
          <Grid.Col xs={24} lg={14}>
            <Card bordered style={CARD_STYLE} bodyStyle={CARD_BODY_STYLE}>
              <Skeleton animation text={{ rows: 4 }} />
            </Card>
          </Grid.Col>
          <Grid.Col xs={24} lg={10}>
            <Card bordered style={CARD_STYLE} bodyStyle={CARD_BODY_STYLE}>
              <Skeleton animation text={{ rows: 4 }} />
            </Card>
          </Grid.Col>
        </Grid.Row>
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="sanaa-page">
        <Card bordered style={CARD_STYLE} bodyStyle={CARD_BODY_STYLE}>
          <Result
            status="warning"
            title={fr.home.loadErrorTitle}
            subTitle={fr.home.loadErrorBody}
            extra={
              <Button type="primary" onClick={() => void query.refetch()}>
                {fr.home.refresh}
              </Button>
            }
          />
        </Card>
      </div>
    );
  }

  const profile = query.data ?? user;
  const roleCard = fr.roleCards[profile.role];
  const details = [
    { key: 'name', label: fr.home.nameLabel, value: profile.fullName },
    { key: 'email', label: fr.home.emailLabel, value: profile.email },
    {
      key: 'role',
      label: fr.home.roleLabel,
      value: <Tag>{fr.roles[profile.role]}</Tag>,
    },
    {
      key: 'tenant',
      label: fr.home.tenantLabel,
      value: (
        <span>
          {branding.name} <Tag>{profile.tenantSlug}</Tag>
        </span>
      ),
    },
  ];

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head">
        <Typography.Title heading={3} className="sanaa-title-xl">
          {fr.home.greeting(profile.fullName)}
        </Typography.Title>
        {sessionStartedAt ? (
          <p className="sanaa-text sanaa-muted">
            {fr.home.sessionSince(formatDate(sessionStartedAt))}
          </p>
        ) : null}
      </header>

      <Grid.Row gutter={[24, 24]}>
        <Grid.Col xs={24} lg={14}>
          <Card
            bordered
            className="sanaa-card sanaa-card--hover"
            style={CARD_STYLE}
            bodyStyle={CARD_BODY_STYLE}
          >
            <h2 className="sanaa-title-md sanaa-card__title">{fr.home.profileTitle}</h2>
            <Descriptions column={1} border data={details} />
          </Card>
        </Grid.Col>

        <Grid.Col xs={24} lg={10}>
          <Card
            bordered
            className="sanaa-card sanaa-card--hover"
            style={CARD_STYLE}
            bodyStyle={CARD_BODY_STYLE}
          >
            <div className="sanaa-role-card__head">
              <span className="sanaa-role-icon" aria-hidden="true">
                {roleIcon(profile.role)}
              </span>
              <div>
                <h2 className="sanaa-title-md">{roleCard.title}</h2>
                <p className="sanaa-caption">{fr.home.spaceTitle}</p>
              </div>
            </div>
            <Tag className="sanaa-role-card__tag">{fr.common.soon}</Tag>
            <p className="sanaa-text sanaa-text--secondary">{roleCard.description}</p>
            <p className="sanaa-caption">{fr.home.spaceSubtitle}</p>
          </Card>
        </Grid.Col>
      </Grid.Row>
    </div>
  );
}
