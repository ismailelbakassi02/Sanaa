package com.sanaa.course.error;

import org.springframework.http.HttpStatus;

/** A request value that is structurally valid but not allowed by a business rule. */
public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Bad Request", detail);
    }
}
