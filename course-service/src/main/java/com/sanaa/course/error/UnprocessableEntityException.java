package com.sanaa.course.error;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable Entity", detail);
    }
}
