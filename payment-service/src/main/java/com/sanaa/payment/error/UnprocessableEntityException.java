package com.sanaa.payment.error;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", detail);
    }
}
