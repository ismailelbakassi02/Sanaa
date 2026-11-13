import { afterEach, describe, expect, it, vi } from 'vitest';

import { openBlobInNewTab, RECEIPT_REVOKE_DELAY_MS } from './receipt';

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('openBlobInNewTab', () => {
  it('opens the receipt and revokes its object URL afterwards', () => {
    vi.useFakeTimers();
    const createObjectURL = vi.fn(() => 'blob:sanaa-receipt');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const open = vi.spyOn(window, 'open').mockReturnValue(null);

    openBlobInNewTab(new Blob(['receipt'], { type: 'image/png' }));

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(open).toHaveBeenCalledWith(
      'blob:sanaa-receipt',
      '_blank',
      'noopener,noreferrer',
    );
    expect(revokeObjectURL).not.toHaveBeenCalled();

    vi.advanceTimersByTime(RECEIPT_REVOKE_DELAY_MS);

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:sanaa-receipt');
  });
});
