import { describe, expect, it } from 'vitest';

import { isPasswordValid, passwordIssue, utf8ByteLength } from './password';

describe('utf8ByteLength', () => {
  it('counts bytes, not characters', () => {
    expect(utf8ByteLength('abc')).toBe(3);
    expect(utf8ByteLength('é')).toBe(2);
    expect(utf8ByteLength('MotDePasseé')).toBe(12);
    expect(utf8ByteLength('😀')).toBe(4);
  });
});

describe('passwordIssue', () => {
  it('rejects fewer than 10 bytes', () => {
    expect(passwordIssue('a'.repeat(9))).toBe('tooShort');
    expect(passwordIssue('éééé')).toBe('tooShort');
  });

  it('accepts exactly the byte bounds', () => {
    expect(passwordIssue('a'.repeat(10))).toBeNull();
    expect(passwordIssue('a'.repeat(72))).toBeNull();
  });

  it('rejects more than 72 bytes even when characters are few', () => {
    expect(passwordIssue('a'.repeat(73))).toBe('tooLong');
    // 36 emoji are only 36 characters but 144 bytes.
    expect(passwordIssue('😀'.repeat(36))).toBe('tooLong');
    expect(isPasswordValid('😀'.repeat(18))).toBe(true);
  });

  it('treats accents as two bytes each', () => {
    expect(isPasswordValid('é'.repeat(4))).toBe(false);
    expect(isPasswordValid('é'.repeat(5))).toBe(true);
    expect(isPasswordValid('é'.repeat(37))).toBe(false);
  });
});
