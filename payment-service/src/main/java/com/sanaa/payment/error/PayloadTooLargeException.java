package com.sanaa.payment.error;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException(String detail) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", detail);
    }
}
