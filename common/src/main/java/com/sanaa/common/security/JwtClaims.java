package com.sanaa.common.security;

/**
 * Claim names and token constants shared by the token issuer and every resource server.
 */
public final class JwtClaims {

    public static final String TENANT_ID = "tenant_id";
    public static final String TENANT_SLUG = "tenant_slug";
    public static final String TENANT_TYPE = "tenant_type";
    public static final String ROLE = "role";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String MUST_CHANGE_PASSWORD = "must_change_password";

    public static final String ISSUER = "sanaa-auth";
    public static final String AUDIENCE_API = "sanaa-api";
    public static final String AUDIENCE_PLAYBACK = "sanaa-playback";

    private JwtClaims() {
    }
}
