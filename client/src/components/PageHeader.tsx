import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  level?: 1 | 2 | 3;
}

export function PageHeader({ title, subtitle, actions, level = 1 }: PageHeaderProps) {
  const Heading = `h${level}` as 'h1' | 'h2' | 'h3';
  return (
    <header className={actions ? 'sanaa-page__head sanaa-page__head--row' : 'sanaa-page__head'}>
      <div className="sanaa-page__head-text">
        <Heading className="sanaa-title-xl">{title}</Heading>
        {subtitle ? <p className="sanaa-text sanaa-muted">{subtitle}</p> : null}
      </div>
      {actions ? <div className="sanaa-page__head-actions">{actions}</div> : null}
    </header>
  );
}
