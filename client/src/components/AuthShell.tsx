import type { ReactNode } from 'react';

import { AppFooter } from './AppFooter';
import { BrandLogo } from './BrandLogo';
import { ThemeToggle } from './ThemeToggle';
import { fr } from '../i18n/fr';

interface AuthShellProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

/** Two-column login and registration shell, with the footer on the form side. */
export function AuthShell({ title, subtitle, children }: AuthShellProps) {
  return (
    <div className="sanaa-auth">
      <ThemeToggle floating />
      <section className="sanaa-auth__brand" aria-label={fr.app.name}>
        <div className="sanaa-auth__brand-main">
          <BrandLogo variant="full" height={72} className="sanaa-auth__logo" />
          <p className="sanaa-auth__tagline">{fr.app.tagline}</p>
          <p className="sanaa-auth__caption">{fr.app.panelCaption}</p>
        </div>
      </section>

      <section className="sanaa-auth__form">
        <div className="sanaa-auth__card">
          <h1 className="sanaa-auth__title">{title}</h1>
          <p className="sanaa-text sanaa-auth__subtitle">{subtitle}</p>
          {children}
        </div>
        <div className="sanaa-auth__footer">
          <AppFooter />
        </div>
      </section>
    </div>
  );
}
