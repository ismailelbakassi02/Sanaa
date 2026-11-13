import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PaymentStatusTag } from './PaymentStatusTag';
import { fr } from '../i18n/fr';

describe('PaymentStatusTag', () => {
  it('maps a pending payment to the gold tag', () => {
    render(<PaymentStatusTag status="PENDING_REVIEW" />);
    expect(
      screen.getByText(fr.payments.statuses.PENDING_REVIEW).closest('.arco-tag'),
    ).toHaveClass('arco-tag-gold');
  });

  it('maps an approved payment to the green tag', () => {
    render(<PaymentStatusTag status="APPROVED" />);
    expect(
      screen.getByText(fr.payments.statuses.APPROVED).closest('.arco-tag'),
    ).toHaveClass('arco-tag-green');
  });

  it('maps a rejected payment to the red tag', () => {
    render(<PaymentStatusTag status="REJECTED" />);
    expect(
      screen.getByText(fr.payments.statuses.REJECTED).closest('.arco-tag'),
    ).toHaveClass('arco-tag-red');
  });
});
