import { Card } from '@arco-design/web-react';

import { PageHeader } from '../components/PageHeader';
import { fr } from '../i18n/fr';

/**
 * Static privacy template. It documents what the product actually does today:
 * memory-only access token, HttpOnly refresh cookie, hashed passwords, audited
 * admin actions. It is a template, not legal advice.
 */
export function PrivacyPage() {
  return (
    <main className="sanaa-page sanaa-page--prose">
      <PageHeader title={fr.privacy.title} subtitle={fr.privacy.updated} />

      <Card bordered className="sanaa-card" bodyStyle={{ padding: 32 }}>
        <div className="sanaa-prose">
          <p className="sanaa-lead">{fr.privacy.intro}</p>
          {fr.privacy.sections.map((section) => (
            <section key={section.title}>
              <h2 className="sanaa-title-lg">{section.title}</h2>
              <p className="sanaa-text">{section.body}</p>
            </section>
          ))}
        </div>
      </Card>
    </main>
  );
}
