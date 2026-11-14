package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class UnsupportedMediaTypeException extends ApiException {

    public UnsupportedMediaTypeException(String detail) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", detail);
    }
}
