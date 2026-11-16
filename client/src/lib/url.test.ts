import { describe, expect, it } from 'vitest';

import {
  isSafeHttpsUrl,
  safeHref,
  sanitizeRedirect,
  urlIssue,
  URL_MAX_LENGTH,
} from './url';

describe('isSafeHttpsUrl', () => {
  it('accepts an absolute HTTPS address', () => {
    expect(isSafeHttpsUrl('https://exemple.com')).toBe(true);
    expect(isSafeHttpsUrl('  https://instagram.com/atelier  ')).toBe(true);
  });

  it('rejects other protocols, relative paths and embedded credentials', () => {
    expect(isSafeHttpsUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeHttpsUrl('data:text/html;base64,PHNjcmlwdD4=')).toBe(false);
    expect(isSafeHttpsUrl('http://exemple.com')).toBe(false);
    expect(isSafeHttpsUrl('/publishers/atelier')).toBe(false);
    expect(isSafeHttpsUrl('https://trusted.example@evil.example')).toBe(false);
    expect(isSafeHttpsUrl('https://user:secret@exemple.com')).toBe(false);
    expect(isSafeHttpsUrl('')).toBe(false);
  });

  it('rejects a value longer than the display limit', () => {
    const long = `https://exemple.com/${'a'.repeat(URL_MAX_LENGTH)}`;
    expect(isSafeHttpsUrl(long)).toBe(false);
  });
});

describe('urlIssue', () => {
  it('treats a blank optional link as valid', () => {
    expect(urlIssue(undefined)).toBeNull();
    expect(urlIssue('')).toBeNull();
    expect(urlIssue('   ')).toBeNull();
  });

  it('distinguishes too long from invalid', () => {
    expect(urlIssue(`https://exemple.com/${'a'.repeat(URL_MAX_LENGTH)}`)).toBe('tooLong');
    expect(urlIssue('ftp://exemple.com')).toBe('invalid');
  });
});

describe('safeHref', () => {
  it('only returns a trimmed, validated address', () => {
    expect(safeHref(' https://exemple.com ')).toBe('https://exemple.com');
    expect(safeHref('javascript:alert(1)')).toBeNull();
    expect(safeHref(null)).toBeNull();
    expect(safeHref(undefined)).toBeNull();
  });
});

describe('sanitizeRedirect', () => {
  it('keeps an internal path with its query and hash', () => {
    expect(sanitizeRedirect('/courses/12?q=photo#avis')).toBe('/courses/12?q=photo#avis');
  });

  it('falls back when nothing is provided', () => {
    expect(sanitizeRedirect(undefined, '/me')).toBe('/me');
    expect(sanitizeRedirect(null, '/me')).toBe('/me');
    expect(sanitizeRedirect('', '/me')).toBe('/me');
  });

  it('rejects every attempt to leave the site', () => {
    expect(sanitizeRedirect('https://evil.example', '/me')).toBe('/me');
    expect(sanitizeRedirect('//evil.example', '/me')).toBe('/me');
    expect(sanitizeRedirect('/\\evil.example', '/me')).toBe('/me');
    expect(sanitizeRedirect('\\\\evil.example', '/me')).toBe('/me');
    expect(sanitizeRedirect('javascript:alert(1)', '/me')).toBe('/me');
    expect(sanitizeRedirect('courses', '/me')).toBe('/me');
  });

  it('rejects a control character that hides the real target', () => {
    expect(sanitizeRedirect('/courses\n//evil.example', '/me')).toBe('/me');
    expect(sanitizeRedirect('/courses\u0000', '/me')).toBe('/me');
  });
});
