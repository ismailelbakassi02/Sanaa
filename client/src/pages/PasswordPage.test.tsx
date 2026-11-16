import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { changePassword, fetchMe, setInitialPassword } from '../api/auth';
import { fr } from '../i18n/fr';
import { renderWithProviders, setAuthUser, studentUser } from '../test/utils';
import { PasswordPage } from './PasswordPage';

vi.mock('../api/auth', () => ({
  changePassword: vi.fn(),
  setInitialPassword: vi.fn(),
  fetchMe: vi.fn(),
  logout: vi.fn(),
}));

const changeMock = vi.mocked(changePassword);
const setInitialMock = vi.mocked(setInitialPassword);
const fetchMeMock = vi.mocked(fetchMe);

beforeEach(() => {
  changeMock.mockReset();
  setInitialMock.mockReset();
  fetchMeMock.mockReset();
});

describe('PasswordPage', () => {
  it('lets a Google-created account define its first normal password', async () => {
    setAuthUser({ ...studentUser, hasPassword: false });
    setInitialMock.mockResolvedValue({ accessToken: 'new-access', expiresIn: 900 });
    fetchMeMock.mockResolvedValue({ ...studentUser, hasPassword: true });
    renderWithProviders(
      <Routes>
        <Route path="/password" element={<PasswordPage />} />
        <Route path="/me" element={<div>page-espace</div>} />
      </Routes>,
      '/password',
    );

    expect(screen.queryByText(fr.password.currentLabel)).not.toBeInTheDocument();
    await userEvent.type(screen.getByPlaceholderText(fr.password.newPlaceholder), 'motdepasse1');
    await userEvent.type(
      screen.getByPlaceholderText(fr.password.confirmPlaceholder),
      'motdepasse1',
    );
    await userEvent.click(screen.getByRole('button', { name: fr.password.submit }));

    expect(setInitialMock).toHaveBeenCalledWith({ newPassword: 'motdepasse1' });
    expect(changeMock).not.toHaveBeenCalled();
    expect(await screen.findByText('page-espace')).toBeInTheDocument();
  });
});
