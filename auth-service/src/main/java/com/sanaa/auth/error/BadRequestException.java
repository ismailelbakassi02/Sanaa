package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Bad Request", detail);
    }
}
