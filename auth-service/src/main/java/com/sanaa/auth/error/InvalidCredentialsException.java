package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

/**
 * Deliberately identical for an unknown email, wrong password and a disabled user. The API must
 * not reveal which one failed, and the login service performs a BCrypt comparison on every path.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password.");
    }
}
