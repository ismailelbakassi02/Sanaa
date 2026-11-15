import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/errors';
import { fetchPublicPublisher } from '../api/publishers';
import type { PublicPublisherResponse } from '../api/types';
import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';
import { PublicPublisherPage } from './PublicPublisherPage';

vi.mock('../api/publishers', () => ({
  fetchPublicPublisher: vi.fn(),
  fetchPublicInstructor: vi.fn(),
  checkSlug: vi.fn(),
  createPublisher: vi.fn(),
  updateMyPublisher: vi.fn(),
}));

const fetchPublisherMock = vi.mocked(fetchPublicPublisher);

function publisher(overrides: Partial<PublicPublisherResponse> = {}): PublicPublisherResponse {
  return {
    slug: 'atelier-lumiere',
    name: 'Atelier Lumière',
    type: 'ORGANIZATION',
    headline: 'Apprendre la vidéo par la pratique',
    description: 'Un collectif de formateurs.',
    websiteUrl: 'javascript:alert(1)',
    instagramUrl: 'https://instagram.com/atelier-lumiere',
    linkedinUrl: 'https://trusted.example@evil.example',
    youtubeUrl: null,
    logoImageId: null,
    primaryColor: '#195A56',
    team: [
      {
        userId: 9,
        fullName: 'Yanis Formateur',
        headline: 'Montage vidéo',
        avatarImageId: null,
      },
    ],
    ...overrides,
  };
}

function renderPublisher(slug = 'atelier-lumiere') {
  return renderWithProviders(
    <Routes>
      <Route path="/publishers/:slug" element={<PublicPublisherPage />} />
    </Routes>,
    `/publishers/${slug}`,
  );
}

beforeEach(() => {
  fetchPublisherMock.mockReset();
});

describe('PublicPublisherPage', () => {
  it('renders the publisher identity, scoped brand color and team links', async () => {
    fetchPublisherMock.mockResolvedValue(publisher());
    const { container } = renderPublisher();

    expect(await screen.findByRole('heading', { name: 'Atelier Lumière' })).toBeVisible();
    expect(screen.getByText(fr.publisherTypes.ORGANIZATION)).toBeVisible();
    expect(screen.getByText('Apprendre la vidéo par la pratique')).toBeVisible();

    const scoped = container.querySelector('.sanaa-public');
    expect(scoped).not.toBeNull();
    // The tenant accent is applied as CSS custom properties on this subtree only.
    expect((scoped as HTMLElement).style.getPropertyValue('--primary-6')).not.toBe('');
    expect((scoped as HTMLElement).style.getPropertyValue('--sanaa-brand-rgb')).toBe('25, 90, 86');

    expect(
      screen.getByRole('link', { name: new RegExp(fr.publicPublisher.memberCta) }),
    ).toHaveAttribute('href', '/instructors/9');
  });

  it('never renders a javascript: or credential-bearing link', async () => {
    fetchPublisherMock.mockResolvedValue(publisher());
    renderPublisher();

    expect(
      await screen.findByRole('link', { name: new RegExp(fr.me.instagramLabel) }),
    ).toHaveAttribute('href', 'https://instagram.com/atelier-lumiere');
    expect(screen.queryByRole('link', { name: new RegExp(fr.me.websiteLabel) })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: new RegExp(fr.me.linkedinLabel) })).not.toBeInTheDocument();
    expect(document.querySelectorAll('a[href^="javascript"]').length).toBe(0);
  });

  it('shows the French 404 when the publisher is unknown', async () => {
    fetchPublisherMock.mockRejectedValue(new ApiError(404, { title: 'Not Found' }));
    renderPublisher('inconnu');

    expect(await screen.findByText(fr.publicPublisher.notFoundTitle)).toBeVisible();
  });

  it('does not render an empty team section for an individual publisher', async () => {
    fetchPublisherMock.mockResolvedValue(
      publisher({ type: 'INDIVIDUAL', team: [] }),
    );
    renderPublisher();

    await screen.findByRole('heading', { name: 'Atelier Lumière' });
    expect(screen.queryByText(fr.publicPublisher.teamTitle)).not.toBeInTheDocument();
  });
});
