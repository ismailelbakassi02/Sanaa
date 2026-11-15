import { describe, expect, it } from 'vitest';

import { ApiError } from './errors';
import { messageForError } from './messages';
import { fr, messageForStatus } from '../i18n/fr';

function conflict(detail: string): ApiError {
  return new ApiError(409, { title: 'Conflict', detail });
}

describe('messageForStatus', () => {
  it('never shows an English ProblemDetail detail', () => {
    expect(messageForStatus(429)).toBe(fr.errors.tooMany);
    expect(messageForStatus(403)).toBe(fr.errors.forbidden);
    expect(messageForStatus(400)).toBe(fr.errors.invalidData);
    expect(messageForStatus(500)).toBe(fr.errors.server);
    expect(messageForStatus(0)).toBe(fr.errors.network);
    expect(messageForStatus(401)).toBe(fr.errors.sessionExpired);
    expect(messageForStatus(401, 'login')).toBe(fr.errors.invalidCredentials);
  });
});

describe('messageForError', () => {
  it('distinguishes an e-mail conflict from a slug conflict', () => {
    expect(messageForError(conflict('This slug is already taken.'))).toBe(fr.errors.slugTaken);
    expect(messageForError(conflict('Email is already registered.'))).toBe(
      fr.errors.emailAlreadyRegistered,
    );
    expect(messageForError(conflict('Email is already registered.'), 'register')).toBe(
      fr.errors.emailAlreadyRegistered,
    );
  });

  it('tells a learner that the account already belongs to a publisher', () => {
    expect(messageForError(conflict('This account already belongs to a publisher.'))).toBe(
      fr.errors.membershipExists,
    );
    expect(
      messageForError(conflict('This account already belongs to a publisher.'), 'team'),
    ).toBe(fr.errors.teamAlreadyMember);
  });

  it('maps the team moderation conflicts', () => {
    expect(messageForError(conflict('You cannot disable your own account.'), 'team')).toBe(
      fr.errors.teamSelfDisable,
    );
    expect(
      messageForError(conflict('The publisher must keep at least one active admin.'), 'team'),
    ).toBe(fr.errors.teamLastAdmin);
    expect(
      messageForError(conflict('Only an organization publisher can manage a team.'), 'team'),
    ).toBe(fr.errors.teamOrganizationOnly);
    expect(messageForError(conflict('Email is already registered.'), 'team')).toBe(
      fr.errors.teamEmailExists,
    );
  });

  it('maps a wrong current password on the password page', () => {
    expect(
      messageForError(
        new ApiError(401, { detail: 'Invalid email or password.' }),
        'password',
      ),
    ).toBe(fr.errors.wrongCurrentPassword);
  });

  it('falls back to a generic French conflict for an unknown detail', () => {
    expect(messageForError(conflict('Something unexpected.'))).toBe(fr.errors.conflict);
  });
});
