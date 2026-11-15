import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchPublicInstructor } from '../api/publishers';
import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';
import { PublicInstructorPage } from './PublicInstructorPage';

vi.mock('../api/publishers', () => ({
  fetchPublicInstructor: vi.fn(),
  fetchPublicPublisher: vi.fn(),
  checkSlug: vi.fn(),
  createPublisher: vi.fn(),
  updateMyPublisher: vi.fn(),
}));

const fetchInstructorMock = vi.mocked(fetchPublicInstructor);

function renderInstructor(id = '7') {
  return renderWithProviders(
    <Routes>
      <Route path="/instructors/:id" element={<PublicInstructorPage />} />
    </Routes>,
    `/instructors/${id}`,
  );
}

beforeEach(() => {
  fetchInstructorMock.mockReset();
});

describe('PublicInstructorPage', () => {
  it('renders a bio containing a script tag as visible text, never as markup', async () => {
    fetchInstructorMock.mockResolvedValue({
      userId: 7,
      fullName: 'Yanis Formateur',
      headline: 'Formateur en montage',
      bio: 'Avant <script>alert("xss")</script> après',
      websiteUrl: null,
      instagramUrl: null,
      linkedinUrl: null,
      youtubeUrl: null,
      avatarImageId: null,
      publisherSlug: 'atelier-lumiere',
      publisherName: 'Atelier Lumière',
    });

    const { container } = renderInstructor();

    expect(await screen.findByText(/Avant <script>alert\("xss"\)<\/script> après/)).toBeVisible();
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Yanis Formateur' })).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'Atelier Lumière' }),
    ).toHaveAttribute('href', '/publishers/atelier-lumiere');
  });

  it('hides a javascript: link and keeps only HTTPS links', async () => {
    fetchInstructorMock.mockResolvedValue({
      userId: 7,
      fullName: 'Yanis Formateur',
      headline: null,
      bio: null,
      websiteUrl: 'javascript:alert(1)',
      instagramUrl: 'https://instagram.com/atelier',
      linkedinUrl: 'http://insecure.example',
      youtubeUrl: null,
      avatarImageId: null,
      publisherSlug: 'atelier-lumiere',
      publisherName: 'Atelier Lumière',
    });

    renderInstructor();

    expect(await screen.findByRole('link', { name: new RegExp(fr.me.instagramLabel) })).toHaveAttribute(
      'href',
      'https://instagram.com/atelier',
    );
    expect(screen.queryByRole('link', { name: new RegExp(fr.me.websiteLabel) })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: new RegExp(fr.me.linkedinLabel) })).not.toBeInTheDocument();
    expect(document.querySelectorAll('a[href^="javascript"]').length).toBe(0);
  });

  it('shows the French 404 for an invalid instructor id', () => {
    renderInstructor('abc');

    expect(screen.getByText(fr.publicInstructor.notFoundTitle)).toBeVisible();
    expect(fetchInstructorMock).not.toHaveBeenCalled();
  });
});
