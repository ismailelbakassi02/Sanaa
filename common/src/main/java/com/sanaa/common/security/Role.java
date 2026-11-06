package com.sanaa.common.security;

/**
 * Platform roles. The Spring Security authority is always the {@code ROLE_} prefixed name.
 */
public enum Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    INSTRUCTOR,
    STUDENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
