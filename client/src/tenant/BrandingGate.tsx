import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { fetchBranding } from '../api/auth';
import { toApiError } from '../api/errors';
import { LoadingScreen } from '../components/LoadingScreen';
import { fr } from '../i18n/fr';
import { BrandingErrorPage } from '../pages/BrandingErrorPage';
import { ServerUnavailablePage } from '../pages/ServerUnavailablePage';
import { TenantNotFoundPage } from '../pages/TenantNotFoundPage';
import { TenantPickerPage } from '../pages/TenantPickerPage';
import { BrandingContext } from './BrandingContext';
import { derivePrimaryScale } from './color';
import { currentTenant } from './detectTenant';

function applyPrimaryScale(hex: string): void {
  const scale = derivePrimaryScale(hex);
  if (!scale) {
    return;
  }
  for (const [step, triplet] of Object.entries(scale)) {
    document.body.style.setProperty(`--primary-${step}`, triplet);
  }
}

function isServerUnavailable(status: number): boolean {
  return status === 0 || status >= 500;
}

export function BrandingGate({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  // After a "choose another organisation" click the picker must win even when
  // the hostname still carries a tenant label.
  const [forcePicker, setForcePicker] = useState(false);

  const detection = useMemo(() => currentTenant(location.search), [location.search]);
  const slug = forcePicker ? null : detection.slug;

  const query = useQuery({
    queryKey: ['branding', slug],
    queryFn: () => fetchBranding(slug ?? ''),
    enabled: slug !== null,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
  });

  const status = query.isError ? toApiError(query.error).status : null;

  useLayoutEffect(() => {
    if (query.data) {
      applyPrimaryScale(query.data.primaryColor);
    }
  }, [query.data]);

  useEffect(() => {
    if (slug === null) {
      document.title = fr.tenantPicker.documentTitle;
    } else if (query.data) {
      document.title = fr.documentTitle(query.data.name);
    } else if (status === 404) {
      document.title = fr.brands.notFoundDocumentTitle;
    }
  }, [query.data, slug, status]);

  const handleSelect = (selected: string) => {
    setForcePicker(false);
    const params = new URLSearchParams(location.search);
    params.set('tenant', selected);
    navigate({ pathname: location.pathname, search: `?${params.toString()}` });
  };

  const handleChooseTenant = () => {
    setForcePicker(true);
    const params = new URLSearchParams(location.search);
    params.delete('tenant');
    const search = params.toString();
    navigate({ pathname: location.pathname, search: search ? `?${search}` : '' });
  };

  if (slug === null) {
    return <TenantPickerPage onSelect={handleSelect} />;
  }
  if (query.isPending) {
    return <LoadingScreen message={fr.brands.loading} />;
  }
  if (query.isError) {
    if (status === 404) {
      return <TenantNotFoundPage slug={slug} onChooseTenant={handleChooseTenant} />;
    }
    if (status !== null && isServerUnavailable(status)) {
      return <ServerUnavailablePage onRetry={() => void query.refetch()} />;
    }
    return <BrandingErrorPage onRetry={() => void query.refetch()} />;
  }

  return (
    <BrandingContext.Provider value={{ slug, branding: query.data }}>
      {children}
    </BrandingContext.Provider>
  );
}
