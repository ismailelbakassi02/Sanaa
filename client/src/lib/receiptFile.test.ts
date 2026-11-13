import { describe, expect, it } from 'vitest';

import { fr } from '../i18n/fr';
import {
  MAX_RECEIPT_BYTES,
  receiptFileMessage,
  validateReceiptFile,
} from './receiptFile';

function fileWith(name: string, type: string, size = 4): File {
  const file = new File(['x'.repeat(size)], name, { type });
  return file;
}

describe('validateReceiptFile', () => {
  it('rejects a missing file', () => {
    expect(validateReceiptFile(null)).toBe('empty');
    expect(receiptFileMessage('empty')).toBe(fr.payments.errors.emptyFile);
  });

  it('rejects an empty file', () => {
    const empty = new File([], 'recu.png', { type: 'image/png' });
    expect(validateReceiptFile(empty)).toBe('empty');
  });

  it('rejects an unsupported type', () => {
    const text = fileWith('notes.txt', 'text/plain');
    expect(validateReceiptFile(text)).toBe('type');
    expect(receiptFileMessage('type')).toBe(fr.payments.errors.unrecognized);
  });

  it('rejects a file larger than 5 Mo', () => {
    const large = fileWith('recu.png', 'image/png');
    Object.defineProperty(large, 'size', { value: MAX_RECEIPT_BYTES + 1 });
    expect(validateReceiptFile(large)).toBe('size');
    expect(receiptFileMessage('size')).toBe(fr.payments.errors.tooLarge);
  });

  it('accepts JPEG, PNG and PDF files at the limit', () => {
    const exact = fileWith('recu.png', 'image/png');
    Object.defineProperty(exact, 'size', { value: MAX_RECEIPT_BYTES });
    expect(validateReceiptFile(exact)).toBeNull();
    expect(validateReceiptFile(fileWith('recu.jpg', 'image/jpeg'))).toBeNull();
    expect(validateReceiptFile(fileWith('recu.pdf', 'application/pdf'))).toBeNull();
  });

  it('accepts a PDF whose browser reports no MIME type', () => {
    expect(validateReceiptFile(fileWith('recu.PDF', ''))).toBeNull();
  });
});
