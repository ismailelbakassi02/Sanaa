import { beforeEach, describe, expect, it } from 'vitest';

import { consumeGoogleRedirect, rememberGoogleRedirect } from './googleAuth';

beforeEach(() => {
  window.sessionStorage.clear();
});

describe('Google OAuth redirect memory', () => {
  it('returns one safe internal destination then clears it', () => {
    rememberGoogleRedirect('/courses/12?from=google');

    expect(consumeGoogleRedirect()).toBe('/courses/12?from=google');
    expect(consumeGoogleRedirect()).toBe('/me');
  });

  it('rejects a protocol-relative hostile destination', () => {
    rememberGoogleRedirect('//evil.example');
    expect(consumeGoogleRedirect()).toBe('/me');
  });
});
