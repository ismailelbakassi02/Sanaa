import { toApiError } from '../api/errors';
import { messageForError } from '../api/messages';
import { fr } from '../i18n/fr';

export type CourseActionContext = 'default' | 'publish';

/**
 * French message for a course mutation failure.
 *
 * 409 always means the optimistic-locking version is stale (or an archived
 * course was edited), so the user is told to reload. 422 only carries domain
 * meaning on publish, where it means "no lesson yet".
 */
export function courseErrorMessage(
  error: unknown,
  context: CourseActionContext = 'default',
): string {
  const status = toApiError(error).status;
  if (status === 409) {
    return fr.editor.conflict;
  }
  if (status === 422) {
    return context === 'publish' ? fr.editor.publishNeedsLesson : fr.errors.invalidData;
  }
  return messageForError(error);
}
