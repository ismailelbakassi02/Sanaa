import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchMe, restoreSession } from '../api/auth';
import { ApiError } from '../api/errors';
import { createPublisher } from '../api/publishers';
import { fr } from '../i18n/fr';
import { BRAND_COLOR } from '../lib/brand';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { BecomeCreatorPage } from './BecomeCreatorPage';

vi.mock('../api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  restoreSession: vi.fn(),
}));

vi.mock('../api/publishers', () => ({
  createPublisher: vi.fn(),
  checkSlug: vi.fn(),
  updateMyPublisher: vi.fn(),
  fetchPublicPublisher: vi.fn(),
  fetchPublicInstructor: vi.fn(),
}));

const createPublisherMock = vi.mocked(createPublisher);
const restoreSessionMock = vi.mocked(restoreSession);
const fetchMeMock = vi.mocked(fetchMe);

function renderPage() {
  return renderWithProviders(<BecomeCreatorPage />, '/become-creator');
}

async function goToStudioStep() {
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: fr.becomeCreator.continue }));
  return user;
}

async function fillStudioStep(user: ReturnType<typeof userEvent.setup>, slug: string) {
  await user.type(
    screen.getByPlaceholderText(fr.becomeCreator.namePlaceholder),
    'Atelier Lumière',
  );
  const slugInput = screen.getByPlaceholderText('mon-studio');
  await user.clear(slugInput);
  await user.type(slugInput, slug);
  await user.type(
    screen.getByPlaceholderText(fr.becomeCreator.emailPlaceholder),
    'bonjour@atelier.example',
  );
  return screen.getByRole('button', { name: fr.becomeCreator.submit });
}

beforeEach(() => {
  setAuthUser(studentUser);
  createPublisherMock.mockReset();
  createPublisherMock.mockResolvedValue({
    slug: 'atelier-lumiere',
    name: 'Atelier Lumière',
    type: 'INDIVIDUAL',
  });
  restoreSessionMock.mockReset();
  restoreSessionMock.mockResolvedValue('refreshed-token');
  fetchMeMock.mockReset();
  fetchMeMock.mockResolvedValue(studentUser);
});

describe('BecomeCreatorPage', () => {
  it('shows the signed-in learner and moves to the studio step', async () => {
    renderPage();

    expect(
      screen.getByText(
        fr.becomeCreator.authenticatedAs(studentUser.fullName, studentUser.email),
      ),
    ).toBeVisible();
    await goToStudioStep();

    expect(
      screen.getByRole('heading', { name: fr.becomeCreator.publisherTitle }),
    ).toBeVisible();
  });

  it('requires the studio name, slug and contact e-mail before calling the API', async () => {
    renderPage();
    const user = await goToStudioStep();

    await user.click(screen.getByRole('button', { name: fr.becomeCreator.submit }));

    expect((await screen.findAllByText(fr.common.required)).length).toBeGreaterThanOrEqual(3);
    expect(createPublisherMock).not.toHaveBeenCalled();
  });

  it('rejects a slug with spaces or capitals with the French rule', async () => {
    renderPage();
    const user = await goToStudioStep();
    const submit = await fillStudioStep(user, 'Atelier Lumière');

    await user.click(submit);

    expect(await screen.findByText(fr.becomeCreator.slugInvalid)).toBeInTheDocument();
    expect(createPublisherMock).not.toHaveBeenCalled();
  });

  it('sends the normalized payload with the brand color and no owner fields', async () => {
    renderPage();
    const user = await goToStudioStep();
    const submit = await fillStudioStep(user, 'atelier-lumiere');

    await waitFor(() => expect(submit).not.toBeDisabled());
    await user.click(submit);

    await waitFor(() => expect(createPublisherMock).toHaveBeenCalledTimes(1));
    expect(createPublisherMock).toHaveBeenCalledWith({
      publisherType: 'INDIVIDUAL',
      publisherName: 'Atelier Lumière',
      slug: 'atelier-lumiere',
      primaryColor: BRAND_COLOR,
      contactEmail: 'bonjour@atelier.example',
    });
  });

  it('shows the French slug conflict when the API returns 409', async () => {
    createPublisherMock.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', detail: 'This slug is already taken.' }),
    );
    renderPage();
    const user = await goToStudioStep();
    const submit = await fillStudioStep(user, 'atelier-lumiere');

    await waitFor(() => expect(submit).not.toBeDisabled());
    await user.click(submit);

    expect(await screen.findByText(fr.becomeCreator.slugTaken)).toBeInTheDocument();
  });

  it('tells a learner who already owns a publisher to open the existing studio', () => {
    setAuthUser({
      ...studentUser,
      role: 'TENANT_ADMIN',
      tenantSlug: 'atelier-lumiere',
      tenantType: 'INDIVIDUAL',
    });
    renderPage();

    expect(screen.getByText(fr.becomeCreator.accountConflictTitle)).toBeVisible();
    expect(
      screen.getByRole('link', { name: fr.becomeCreator.accountConflictAction }),
    ).toHaveAttribute('href', '/studio/profile');
  });

  it('tells a platform administrator that a separate learner account is required', () => {
    setAuthUser({ ...studentUser, role: 'SUPER_ADMIN' });
    renderPage();

    expect(screen.getByText(fr.becomeCreator.accountAdminTitle)).toBeVisible();
    expect(
      screen.getByRole('link', { name: fr.becomeCreator.accountAdminAction }),
    ).toHaveAttribute('href', '/me');
  });
});
