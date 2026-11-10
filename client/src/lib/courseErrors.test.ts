import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/errors';
import { fr } from '../i18n/fr';
import { courseErrorMessage } from './courseErrors';

describe('courseErrorMessage', () => {
  it('maps a stale version to the reload message', () => {
    expect(courseErrorMessage(new ApiError(409, null))).toBe(fr.editor.conflict);
  });

  it('maps 422 on publish to the missing lesson message', () => {
    expect(courseErrorMessage(new ApiError(422, null), 'publish')).toBe(
      fr.editor.publishNeedsLesson,
    );
  });

  it('maps 422 elsewhere to the generic invalid data message', () => {
    expect(courseErrorMessage(new ApiError(422, null))).toBe(fr.errors.invalidData);
  });

  it('maps 403 and 404 to the existing French messages', () => {
    expect(courseErrorMessage(new ApiError(403, null))).toBe(fr.errors.forbidden);
    expect(courseErrorMessage(new ApiError(404, null))).toBe(fr.errors.notFound);
  });

  it('maps network failures', () => {
    expect(courseErrorMessage(new ApiError(0, null))).toBe(fr.errors.network);
  });
});
