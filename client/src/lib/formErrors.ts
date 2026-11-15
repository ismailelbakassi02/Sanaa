import { fieldErrors, toApiError } from '../api/errors';
import { fr } from '../i18n/fr';

/**
 * Arco's Form.setFields is keyed by field name and typed per form shape. The
 * parameter is widened to `object` so a single helper serves every form: the
 * runtime contract (a keyed record) is what Arco actually expects.
 */
export interface FieldErrorSink {
  setFields: (fields: object) => void;
}

const PASSWORD_FIELDS = new Set(['password', 'newPassword', 'currentPassword', 'temporaryPassword']);
const EMAIL_FIELDS = new Set(['email', 'ownerEmail', 'contactEmail']);
const NAME_FIELDS = new Set(['fullName', 'name', 'publisherName', 'ownerFullName']);
const LINK_FIELDS = new Set(['websiteUrl', 'instagramUrl', 'linkedinUrl', 'youtubeUrl']);

/**
 * The backend validation messages are English implementation details. Known
 * fields get the same French rule the client already enforces; an unknown field
 * falls back to the generic message rather than leaking server text.
 */
export function frenchFieldMessage(field: string): string {
  if (EMAIL_FIELDS.has(field)) {
    return fr.validation.email;
  }
  if (field === 'temporaryPassword') {
    return fr.validation.temporaryPassword;
  }
  if (PASSWORD_FIELDS.has(field)) {
    return fr.validation.password;
  }
  if (field === 'slug') {
    return fr.validation.slug;
  }
  if (field === 'primaryColor') {
    return fr.validation.color;
  }
  if (NAME_FIELDS.has(field)) {
    return fr.validation.name;
  }
  if (LINK_FIELDS.has(field)) {
    return fr.validation.url;
  }
  if (field === 'reason') {
    return fr.validation.reason;
  }
  return fr.errors.invalidData;
}

/**
 * Applies a 400 Bean Validation body to an Arco form. Returns true when at
 * least one field was marked, so the caller can skip the toast.
 */
export function applyServerFieldErrors(form: FieldErrorSink, error: unknown): boolean {
  const api = toApiError(error);
  if (api.status !== 400) {
    return false;
  }
  const entries = Object.keys(fieldErrors(api.problem));
  if (entries.length === 0) {
    return false;
  }
  const fields: Record<string, { error: { message: string } }> = {};
  for (const field of entries) {
    fields[field] = { error: { message: frenchFieldMessage(field) } };
  }
  form.setFields(fields);
  return true;
}
