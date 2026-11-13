package com.sanaa.course.event;

/**
 * A failure that retrying cannot fix: malformed JSON, an invalid envelope or an event that
 * references data which will never exist. The error handler sends such a record straight to the
 * dead letter topic instead of retrying it.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
