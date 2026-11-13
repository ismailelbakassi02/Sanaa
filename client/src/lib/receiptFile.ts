import { fr } from '../i18n/fr';

/** The server is the authority, but the UI rejects bad files before uploading. */
export const MAX_RECEIPT_BYTES = 5 * 1024 * 1024;

export const RECEIPT_ACCEPT = '.jpg,.jpeg,.png,.pdf';

const ACCEPTED_MIME_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];
const ACCEPTED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.pdf'];

export type ReceiptFileProblem = 'empty' | 'type' | 'size';

/**
 * Checks the file the student selected. Extensions are accepted when the
 * browser reports no MIME type, because some desktop environments leave
 * file.type empty for PDFs.
 */
export function validateReceiptFile(file: File | null | undefined): ReceiptFileProblem | null {
  if (!file) {
    return 'empty';
  }
  const name = file.name.toLowerCase();
  const hasAcceptedType =
    ACCEPTED_MIME_TYPES.includes(file.type) || file.type === '';
  const hasAcceptedExtension = ACCEPTED_EXTENSIONS.some((extension) =>
    name.endsWith(extension),
  );
  if (!hasAcceptedType || !hasAcceptedExtension) {
    return 'type';
  }
  if (file.size === 0) {
    return 'empty';
  }
  if (file.size > MAX_RECEIPT_BYTES) {
    return 'size';
  }
  return null;
}

export function receiptFileMessage(problem: ReceiptFileProblem): string {
  switch (problem) {
    case 'empty':
      return fr.payments.errors.emptyFile;
    case 'type':
      return fr.payments.errors.unrecognized;
    case 'size':
      return fr.payments.errors.tooLarge;
  }
}
