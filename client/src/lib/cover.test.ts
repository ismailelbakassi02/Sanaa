import { describe, expect, it } from 'vitest';

import { coverFamily, initialsOf } from './cover';

describe('initialsOf', () => {
  it('keeps the first letter of the first two words', () => {
    expect(initialsOf('Étalonnage et colorimétrie')).toBe('ÉE');
    expect(initialsOf('  montage   vidéo  ')).toBe('MV');
  });

  it('falls back to a question mark for an empty title', () => {
    expect(initialsOf('   ')).toBe('?');
  });
});

describe('coverFamily', () => {
  it('is deterministic for the same title', () => {
    expect(coverFamily('Montage vidéo')).toBe(coverFamily('Montage vidéo'));
  });

  it('ignores case and surrounding spaces', () => {
    expect(coverFamily('  Montage vidéo ')).toBe(coverFamily('montage vidéo'));
  });

  it('changes with the title and always stays in the known families', () => {
    const families = new Set([
      coverFamily('Montage vidéo'),
      coverFamily('Photographie'),
      coverFamily('Cuisine'),
      coverFamily('Dessin'),
    ]);
    expect(families.size).toBeGreaterThan(1);
    for (const family of families) {
      expect([
        'arcoblue',
        'purple',
        'magenta',
        'red',
        'orange',
        'gold',
        'green',
        'cyan',
      ]).toContain(family);
    }
  });
});
