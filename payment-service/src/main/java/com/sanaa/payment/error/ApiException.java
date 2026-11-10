package com.sanaa.payment.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map directly to an RFC 7807 response. The detail is written for the
 * client; stack traces and internal state never leave the service.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
