package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required.");
    }
}
