import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/errors';
import { fr } from '../i18n/fr';
import { adminPaymentErrorMessage, paymentErrorMessage } from './paymentErrors';

describe('paymentErrorMessage', () => {
  it('maps the five payment failures to French messages', () => {
    expect(paymentErrorMessage(new ApiError(409, null))).toBe(
      fr.payments.errors.conflict,
    );
    expect(paymentErrorMessage(new ApiError(413, null))).toBe(
      fr.payments.errors.tooLarge,
    );
    expect(paymentErrorMessage(new ApiError(422, null))).toBe(
      fr.payments.errors.unrecognized,
    );
    expect(paymentErrorMessage(new ApiError(503, null))).toBe(
      fr.payments.errors.unavailable,
    );
    expect(paymentErrorMessage(new ApiError(0, null))).toBe(fr.errors.network);
  });

  it('falls back to the generic server error', () => {
    expect(paymentErrorMessage(new ApiError(500, null))).toBe(fr.errors.server);
    expect(paymentErrorMessage(new ApiError(418, null))).toBe(fr.errors.unknown);
  });
});

describe('adminPaymentErrorMessage', () => {
  it('uses the dedicated message for an already processed payment', () => {
    expect(adminPaymentErrorMessage(new ApiError(409, null))).toBe(
      fr.payments.admin.alreadyProcessed,
    );
  });

  it('keeps the generic payment mapping for other failures', () => {
    expect(adminPaymentErrorMessage(new ApiError(503, null))).toBe(
      fr.payments.errors.unavailable,
    );
  });
});
