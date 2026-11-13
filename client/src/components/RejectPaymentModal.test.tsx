import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { RejectPaymentModal } from './RejectPaymentModal';
import { fr } from '../i18n/fr';
import { renderWithProviders } from '../test/utils';

const baseProps = {
  visible: true,
  studentName: 'Atlas Student One',
  courseTitle: 'Montage vidéo : les bases',
  submitting: false,
  onCancel: vi.fn(),
};

describe('RejectPaymentModal', () => {
  it('refuses an empty reason and keeps the payment untouched', async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<RejectPaymentModal {...baseProps} onSubmit={onSubmit} />);

    await userEvent.click(
      screen.getByRole('button', { name: fr.payments.admin.confirmReject }),
    );

    expect(await screen.findByText(fr.payments.admin.rejectTooShort)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a trimmed reason of at least three characters', async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<RejectPaymentModal {...baseProps} onSubmit={onSubmit} />);

    await userEvent.type(
      screen.getByRole('textbox'),
      '  Justificatif illisible  ',
    );
    await userEvent.click(
      screen.getByRole('button', { name: fr.payments.admin.confirmReject }),
    );

    expect(onSubmit).toHaveBeenCalledWith('Justificatif illisible');
  });
});
