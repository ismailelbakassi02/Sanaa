/**
 * The one password rule: 10 to 72 bytes in UTF-8. Bytes are counted because
 * BCrypt truncates at 72 bytes, so accented or emoji passwords can lose
 * entropy even when the character count looks short.
 */
export const PASSWORD_MIN_BYTES = 10;
export const PASSWORD_MAX_BYTES = 72;

export type PasswordIssue = 'tooShort' | 'tooLong' | null;

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

export function passwordIssue(value: string): PasswordIssue {
  const bytes = utf8ByteLength(value);
  if (bytes < PASSWORD_MIN_BYTES) {
    return 'tooShort';
  }
  if (bytes > PASSWORD_MAX_BYTES) {
    return 'tooLong';
  }
  return null;
}

export function isPasswordValid(value: string): boolean {
  return passwordIssue(value) === null;
}
