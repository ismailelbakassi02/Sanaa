import { Button, Card, Grid, Tag } from '@arco-design/web-react';
import { IconCheck, IconRight } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';

import { BrandLogo } from '../components/BrandLogo';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

export function HomePage() {
  const user = useAuthStore((state) => state.user);
  const primaryPath = user ? (user.mustChangePassword ? '/password' : '/me') : '/register';
  const primaryLabel = user ? fr.home.openSpace : fr.home.startLearning;

  return (
    <main className="sanaa-home">
      <section className="sanaa-hero">
        <div className="sanaa-hero__content">
          <Tag className="sanaa-hero__eyebrow">{fr.app.headerTagline}</Tag>
          <h1 className="sanaa-display">{fr.home.heroTitle}</h1>
          <p className="sanaa-lead">{fr.home.heroLead}</p>
          <div className="sanaa-hero__actions">
            <Link to={primaryPath}>
              <Button type="primary" size="large" icon={<IconRight />}>
                {primaryLabel}
              </Button>
            </Link>
            <Link to="/become-creator">
              <Button size="large">{fr.home.becomeCreator}</Button>
            </Link>
          </div>
          <Link to="/privacy" className="sanaa-hero__privacy sanaa-link">
            {fr.home.privacyLink}
          </Link>
        </div>

        <div className="sanaa-hero__panel">
          <BrandLogo variant="full" height={88} />
          <p className="sanaa-hero__panel-text">{fr.app.panelCaption}</p>
          <ul className="sanaa-hero__points">
            <li>
              <IconCheck aria-hidden="true" />
              {fr.home.learnerPoints[0]}
            </li>
            <li>
              <IconCheck aria-hidden="true" />
              {fr.home.creatorPoints[0]}
            </li>
            <li>
              <IconCheck aria-hidden="true" />
              {fr.home.creatorPoints[2]}
            </li>
          </ul>
        </div>
      </section>

      <section className="sanaa-audience" aria-label={fr.app.name}>
        <Grid.Row gutter={[24, 24]}>
          <Grid.Col xs={24} lg={12}>
            <Card bordered className="sanaa-card sanaa-audience__card">
              <h2 className="sanaa-title-lg">{fr.home.learnerTitle}</h2>
              <p className="sanaa-text sanaa-muted">{fr.home.learnerLead}</p>
              <ul className="sanaa-points">
                {fr.home.learnerPoints.map((point) => (
                  <li key={point}>
                    <IconCheck aria-hidden="true" />
                    {point}
                  </li>
                ))}
              </ul>
            </Card>
          </Grid.Col>
          <Grid.Col xs={24} lg={12}>
            <Card bordered className="sanaa-card sanaa-audience__card">
              <h2 className="sanaa-title-lg">{fr.home.creatorTitle}</h2>
              <p className="sanaa-text sanaa-muted">{fr.home.creatorLead}</p>
              <ul className="sanaa-points">
                {fr.home.creatorPoints.map((point) => (
                  <li key={point}>
                    <IconCheck aria-hidden="true" />
                    {point}
                  </li>
                ))}
              </ul>
            </Card>
          </Grid.Col>
        </Grid.Row>
      </section>

      <section className="sanaa-callout">
        <div>
          <h2 className="sanaa-title-lg">{fr.home.calloutTitle}</h2>
          <p className="sanaa-text sanaa-muted">{fr.home.calloutBody}</p>
        </div>
        <Link to="/become-creator">
          <Button type="primary" size="large">
            {fr.home.calloutAction}
          </Button>
        </Link>
      </section>
    </main>
  );
}
