package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class SlugAlreadyUsedException extends ApiException {

    public SlugAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "Conflict", "Tenant slug is already in use.");
    }
}
