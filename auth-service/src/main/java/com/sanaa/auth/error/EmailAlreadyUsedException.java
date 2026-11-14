package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

/**
 * Emails are globally unique now: the same address cannot be used by a learner, a publisher owner
 * or a team member anywhere on the platform.
 */
public class EmailAlreadyUsedException extends ApiException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "Conflict", "Email is already registered.");
    }
}
