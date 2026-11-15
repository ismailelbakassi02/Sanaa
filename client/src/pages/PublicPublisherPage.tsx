import { Avatar, Button, Card, Grid, Result, Skeleton, Tag } from '@arco-design/web-react';
import { IconLink } from '@arco-design/web-react/icon';
import type { CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';

import { publicImageUrl } from '../api/profile';
import { usePublicPublisher } from '../hooks/publishers';
import { fr } from '../i18n/fr';
import { BRAND_COLOR } from '../lib/brand';
import { derivePrimaryScale, hexToRgb, isHexColor, toCssTriplet } from '../lib/color';
import { safeHref } from '../lib/url';
import { useThemeStore } from '../store/theme';

function publisherStyle(color: string, mode: 'light' | 'dark'): CSSProperties {
  const rgb = hexToRgb(color);
  const scale = derivePrimaryScale(color, mode);
  const style: Record<string, string> = {
    '--sanaa-brand-rgb': rgb ? toCssTriplet(rgb) : '',
  };
  if (scale) {
    for (const [step, triplet] of Object.entries(scale)) {
      style[`--primary-${step}`] = triplet;
    }
  }
  return style as CSSProperties;
}

const LINKS = [
  { key: 'websiteUrl', label: fr.me.websiteLabel },
  { key: 'instagramUrl', label: fr.me.instagramLabel },
  { key: 'linkedinUrl', label: fr.me.linkedinLabel },
  { key: 'youtubeUrl', label: fr.me.youtubeLabel },
] as const;

export function PublicPublisherPage() {
  const { slug } = useParams<{ slug: string }>();
  const query = usePublicPublisher(slug);
  const mode = useThemeStore((state) => state.mode);

  if (query.isPending) {
    return (
      <div className="sanaa-page sanaa-page--narrow">
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 32 }}>
          <Skeleton animation text={{ rows: 6 }} />
        </Card>
      </div>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="sanaa-center-page">
        <Result
          status="404"
          title={fr.publicPublisher.notFoundTitle}
          subTitle={fr.publicPublisher.notFoundBody}
          extra={
            <Link to="/">
              <Button type="primary">{fr.common.backHome}</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const publisher = query.data;
  const color = isHexColor(publisher.primaryColor ?? '') ? (publisher.primaryColor as string) : BRAND_COLOR;
  const logo = publicImageUrl(publisher.logoImageId);
  // An empty "Retrouver ce créateur" heading looked like a broken section, so
  // the block only exists when at least one safe link is present.
  const links = LINKS.flatMap((link) => {
    const href = safeHref(publisher[link.key]);
    return href ? [{ key: link.key, label: link.label, href }] : [];
  });

  return (
    <div className="sanaa-public" style={publisherStyle(color, mode)}>
      <header className="sanaa-public__header">
        {logo ? (
          <img className="sanaa-public__logo" src={logo} alt="" />
        ) : (
          <Avatar size={96} className="sanaa-public__avatar">
            {publisher.name.charAt(0).toUpperCase()}
          </Avatar>
        )}
        <div className="sanaa-public__identity">
          <Tag color={publisher.type === 'ORGANIZATION' ? 'arcoblue' : 'green'}>
            {fr.publisherTypes[publisher.type]}
          </Tag>
          <h1 className="sanaa-display sanaa-display--compact">{publisher.name}</h1>
          {publisher.headline ? <p className="sanaa-lead">{publisher.headline}</p> : null}
        </div>
      </header>

      {publisher.description ? (
        <Card bordered className="sanaa-card sanaa-public__about" bodyStyle={{ padding: 28 }}>
          <p className="sanaa-text sanaa-prose-text">{publisher.description}</p>
        </Card>
      ) : null}

      {links.length > 0 ? (
        <section className="sanaa-public__links" aria-label={fr.publicPublisher.linksTitle}>
          <h2 className="sanaa-title-md">{fr.publicPublisher.linksTitle}</h2>
          <ul className="sanaa-link-list sanaa-link-list--inline">
            {links.map((link) => (
              <li key={link.key}>
                <a className="sanaa-link" href={link.href} target="_blank" rel="noreferrer noopener">
                  <IconLink aria-hidden="true" /> {link.label}
                </a>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {publisher.type === 'ORGANIZATION' && publisher.team.length > 0 ? (
        <section className="sanaa-public__team">
          <h2 className="sanaa-title-lg">{fr.publicPublisher.teamTitle}</h2>
          <p className="sanaa-text sanaa-muted">{fr.publicPublisher.teamLead}</p>
          <Grid.Row gutter={[24, 24]}>
            {publisher.team.map((member) => (
              <Grid.Col key={member.userId} xs={24} sm={12} lg={8}>
                <Link
                  to={`/instructors/${member.userId}`}
                  className="sanaa-course-card-link"
                  aria-label={`${fr.publicPublisher.memberCta} : ${member.fullName}`}
                >
                  <Card bordered className="sanaa-card sanaa-public__member">
                    <Avatar size={48}>
                      {member.avatarImageId ? (
                        <img src={publicImageUrl(member.avatarImageId) ?? ''} alt="" />
                      ) : (
                        member.fullName.charAt(0).toUpperCase()
                      )}
                    </Avatar>
                    <div>
                      <h3 className="sanaa-title-md">{member.fullName}</h3>
                      {member.headline ? (
                        <p className="sanaa-caption">{member.headline}</p>
                      ) : null}
                    </div>
                  </Card>
                </Link>
              </Grid.Col>
            ))}
          </Grid.Row>
        </section>
      ) : null}
    </div>
  );
}
