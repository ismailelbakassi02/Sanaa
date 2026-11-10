package com.sanaa.course.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected API failures. The message is safe to return to the client; stack traces
 * are never exposed.
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
