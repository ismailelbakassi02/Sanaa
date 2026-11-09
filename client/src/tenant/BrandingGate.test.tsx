import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchBranding } from '../api/auth';
import { ApiError } from '../api/errors';
import type { BrandingResponse } from '../api/types';
import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';
import { BrandingGate } from './BrandingGate';

vi.mock('../api/auth', () => ({
  fetchBranding: vi.fn(),
}));

const fetchBrandingMock = vi.mocked(fetchBranding);

const branding: BrandingResponse = {
  name: 'Atlas Academy',
  primaryColor: '#00B42A',
  logoUrl: null,
  registrationEnabled: true,
};

function renderGate(route = '/') {
  return renderWithProviders(
    <BrandingGate>
      <div>contenu-protégé</div>
    </BrandingGate>,
    route,
  );
}

beforeEach(() => {
  fetchBrandingMock.mockReset();
  fetchBrandingMock.mockResolvedValue(branding);
  vi.stubEnv('VITE_DEFAULT_TENANT', '');
  vi.stubEnv('VITE_BASE_DOMAIN', 'localhost');
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('BrandingGate', () => {
  it('shows the organisation picker without calling the API', () => {
    renderGate('/');

    expect(
      screen.getByRole('heading', { name: fr.tenantPicker.title, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByPlaceholderText(fr.tenantPicker.slugPlaceholder)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: fr.tenantPicker.submit })).toBeInTheDocument();
    expect(fetchBrandingMock).not.toHaveBeenCalled();
  });

  it('rejects an invalid slug without calling the API', async () => {
    const user = userEvent.setup();
    renderGate('/');

    await user.type(screen.getByPlaceholderText(fr.tenantPicker.slugPlaceholder), 'x');
    await user.click(screen.getByRole('button', { name: fr.tenantPicker.submit }));

    expect(screen.getByRole('alert')).toHaveTextContent(fr.tenantPicker.invalidSlug);
    expect(fetchBrandingMock).not.toHaveBeenCalled();
  });

  it('normalises the slug then loads branding through ?tenant=', async () => {
    const user = userEvent.setup();
    renderGate('/');

    await user.type(screen.getByPlaceholderText(fr.tenantPicker.slugPlaceholder), ' ATLAS ');
    await user.click(screen.getByRole('button', { name: fr.tenantPicker.submit }));

    await waitFor(() => expect(fetchBrandingMock).toHaveBeenCalledWith('atlas'));
    expect(await screen.findByText('contenu-protégé')).toBeInTheDocument();
  });

  it('shows the not-found screen and returns to the picker', async () => {
    fetchBrandingMock.mockRejectedValue(new ApiError(404, null));
    const user = userEvent.setup();
    renderGate('/?tenant=inexistant');

    expect(await screen.findByText(fr.brands.notFoundTitle)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: fr.brands.chooseTenant }));

    expect(
      screen.getByRole('heading', { name: fr.tenantPicker.title, level: 1 }),
    ).toBeInTheDocument();
    expect(fetchBrandingMock).toHaveBeenCalledWith('inexistant');
  });

  it('shows the server screen for a network failure and retries', async () => {
    fetchBrandingMock.mockRejectedValue(new ApiError(0, null));
    const user = userEvent.setup();
    renderGate('/?tenant=atlas');

    expect(await screen.findByText(fr.brands.unavailableTitle)).toBeInTheDocument();
    expect(screen.getByText(fr.brands.unavailableBody)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: fr.common.retry }));
    await waitFor(() => expect(fetchBrandingMock).toHaveBeenCalledTimes(2));
  });

  it('shows the server screen for a 5xx failure', async () => {
    fetchBrandingMock.mockRejectedValue(new ApiError(503, null));
    renderGate('/?tenant=atlas');

    expect(await screen.findByText(fr.brands.unavailableTitle)).toBeInTheDocument();
  });

  it('shows the generic error for any other failure', async () => {
    fetchBrandingMock.mockRejectedValue(new ApiError(400, null));
    renderGate('/?tenant=atlas');

    expect(await screen.findByText(fr.brands.genericTitle)).toBeInTheDocument();
    expect(screen.queryByText(fr.brands.unavailableTitle)).not.toBeInTheDocument();
  });
});
