package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends ApiException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "Conflict", "Email is already registered in this tenant.");
    }
}
