import { toApiError } from '../api/errors';
import { fr } from '../i18n/fr';

/**
 * Payment errors are mapped here instead of reusing messageForStatus: a 409 is
 * an existing open payment or enrollment, not a duplicate account, and a 422
 * is an unreadable receipt even though it usually means invalid data.
 */
export function paymentErrorMessage(error: unknown): string {
  const status = toApiError(error).status;
  switch (status) {
    case 409:
      return fr.payments.errors.conflict;
    case 413:
      return fr.payments.errors.tooLarge;
    case 422:
      return fr.payments.errors.unrecognized;
    case 503:
      return fr.payments.errors.unavailable;
    case 400:
      return fr.errors.invalidData;
    case 403:
      return fr.errors.forbidden;
    case 404:
      return fr.errors.notFound;
    case 0:
      return fr.errors.network;
    default:
      return status >= 500 ? fr.errors.server : fr.errors.unknown;
  }
}

/** The review endpoints answer 409 when the payment was already processed. */
export function adminPaymentErrorMessage(error: unknown): string {
  if (toApiError(error).status === 409) {
    return fr.payments.admin.alreadyProcessed;
  }
  return paymentErrorMessage(error);
}
