import { fr, messageForStatus, type ErrorContext } from '../i18n/fr';
import { toApiError } from './errors';

function detailOf(error: unknown): string {
  return toApiError(error).problem?.detail?.toLowerCase() ?? '';
}

/**
 * French message for an API failure. Conflicts are indistinguishable by status
 * alone, so the English ProblemDetail detail selects the precise French text:
 * an e-mail conflict must not read "Cette adresse est déjà prise."
 */
export function messageForError(error: unknown, context: ErrorContext = 'default'): string {
  const api = toApiError(error);
  const detail = detailOf(error);

  if (api.status === 409) {
    if (context === 'register' || detail.includes('email is already registered')) {
      return context === 'team' ? fr.errors.teamEmailExists : fr.errors.emailAlreadyRegistered;
    }
    if (context === 'slug' || detail.includes('slug is already taken')) {
      return fr.errors.slugTaken;
    }
    if (detail.includes('already belongs to a publisher')) {
      return context === 'team' ? fr.errors.teamAlreadyMember : fr.errors.membershipExists;
    }
    if (detail.includes('cannot disable your own account')) {
      return fr.errors.teamSelfDisable;
    }
    if (detail.includes('at least one active admin')) {
      return fr.errors.teamLastAdmin;
    }
    if (detail.includes('only an organization')) {
      return fr.errors.teamOrganizationOnly;
    }
    return fr.errors.conflict;
  }

  if (api.status === 401 && context === 'password' && detail.includes('invalid email or password')) {
    return fr.errors.wrongCurrentPassword;
  }

  return messageForStatus(api.status, context);
}
