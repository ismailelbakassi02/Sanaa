import type { ReactNode } from 'react';

import { BrandMark } from '../components/BrandMark';
import { ThemeToggle } from '../components/ThemeToggle';
import { fr } from '../i18n/fr';
import { useBranding } from '../tenant/BrandingContext';

interface AuthShellProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

export function AuthShell({ title, subtitle, children }: AuthShellProps) {
  const { branding } = useBranding();

  return (
    <div className="sanaa-auth">
      <ThemeToggle floating />
      <section className="sanaa-auth__brand" aria-label={branding.name}>
        <div className="sanaa-auth__brand-main">
          <div className="sanaa-brand sanaa-brand--large">
            <BrandMark large name={branding.name} logoUrl={branding.logoUrl} />
            <span className="sanaa-brand__name sanaa-brand__name--large">{branding.name}</span>
          </div>
          <p className="sanaa-auth__tagline">{fr.app.tagline}</p>
          <p className="sanaa-auth__caption">{fr.app.panelCaption}</p>
        </div>
        <p className="sanaa-auth__powered">{fr.app.name}</p>
      </section>

      <section className="sanaa-auth__form">
        <div className="sanaa-auth__card">
          <h1 className="sanaa-title-xl">{title}</h1>
          <p className="sanaa-text sanaa-muted sanaa-auth__subtitle">{subtitle}</p>
          {children}
        </div>
      </section>
    </div>
  );
}
