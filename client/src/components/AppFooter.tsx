import { Link } from 'react-router-dom';

import { BrandLogo } from './BrandLogo';
import { fr } from '../i18n/fr';

/** The same footer on every page: brand mark, printable name and privacy link. */
export function AppFooter() {
  return (
    <footer className="sanaa-footer">
      <div className="sanaa-footer__inner">
        <div className="sanaa-footer__brand">
          <BrandLogo variant="mark" height={18} />
          <span className="sanaa-footer__name">{fr.app.name}</span>
        </div>
        <nav aria-label={fr.nav.privacy}>
          <Link className="sanaa-footer__link" to="/privacy">
            {fr.nav.privacy}
          </Link>
        </nav>
      </div>
    </footer>
  );
}
