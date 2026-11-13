/**
 * A receipt download always arrives as an attachment, with the verified
 * content type. The caller opens the blob in a new tab through a temporary
 * object URL and revokes it once the tab has had time to read it.
 */
export const RECEIPT_REVOKE_DELAY_MS = 1000;

export function openBlobInNewTab(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener,noreferrer');
  window.setTimeout(() => URL.revokeObjectURL(url), RECEIPT_REVOKE_DELAY_MS);
}
