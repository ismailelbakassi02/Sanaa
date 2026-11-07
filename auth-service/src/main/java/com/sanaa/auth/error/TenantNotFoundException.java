package com.sanaa.auth.error;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends ApiException {

    public TenantNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Not Found", "Tenant not found or inactive.");
    }
}
