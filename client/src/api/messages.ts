import { messageForStatus, type ErrorContext } from '../i18n/fr';
import { toApiError } from './errors';

export function messageForError(error: unknown, context: ErrorContext = 'default'): string {
  return messageForStatus(toApiError(error).status, context);
}
