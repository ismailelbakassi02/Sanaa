import { Navigate, useParams } from 'react-router-dom';

import { serializeCatalogueSearch, DEFAULT_FILTERS } from '../lib/searchParams';

/**
 * Category pages keep a stable public address while the catalogue stays the
 * only place that renders results: `/categories/:slug` rewrites to
 * `/courses?category=:slug`, filters included.
 */
export function CategoryPage() {
  const { slug } = useParams<{ slug: string }>();
  const search = serializeCatalogueSearch({
    ...DEFAULT_FILTERS,
    category: slug ? slug.trim() : '',
  });
  return <Navigate to={search ? `/courses?${search}` : '/courses'} replace />;
}
