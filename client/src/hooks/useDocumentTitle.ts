import { useEffect } from 'react';

import { fr } from '../i18n/fr';

/**
 * A single place for the browser tab title. Every page passes its French
 * heading, so history and bookmarks stay recognisable.
 */
export function useDocumentTitle(title: string | null | undefined): void {
  useEffect(() => {
    document.title = title ? fr.documentTitleTemplate(title) : fr.documentTitle;
  }, [title]);
}
