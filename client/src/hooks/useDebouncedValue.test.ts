import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useDebouncedValue } from './useDebouncedValue';

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps the previous value during the delay', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'e' },
    });

    expect(result.current).toBe('e');
    rerender({ value: 'et' });
    expect(result.current).toBe('e');

    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(result.current).toBe('e');

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('et');
  });

  it('only publishes the last value after rapid typing', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'e' },
    });

    for (const value of ['et', 'eta', 'etal']) {
      rerender({ value });
      act(() => {
        vi.advanceTimersByTime(100);
      });
    }

    expect(result.current).toBe('e');
    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBe('etal');
  });
});
