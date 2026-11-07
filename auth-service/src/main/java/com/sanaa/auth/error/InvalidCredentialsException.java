package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

/**
 * Deliberately identical for an unknown tenant slug, unknown email, wrong password, disabled user
 * and suspended tenant. The API must not reveal which one failed.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid tenant, email or password.");
    }
}
