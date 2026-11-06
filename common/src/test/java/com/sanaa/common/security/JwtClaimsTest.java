package com.sanaa.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtClaimsTest {

    @Test
    void claimNamesAndTokenConstantsMatchTheContract() {
        assertThat(JwtClaims.TENANT_ID).isEqualTo("tenant_id");
        assertThat(JwtClaims.TENANT_SLUG).isEqualTo("tenant_slug");
        assertThat(JwtClaims.ROLE).isEqualTo("role");
        assertThat(JwtClaims.EMAIL).isEqualTo("email");
        assertThat(JwtClaims.NAME).isEqualTo("name");
        assertThat(JwtClaims.ISSUER).isEqualTo("sanaa-auth");
        assertThat(JwtClaims.AUDIENCE_API).isEqualTo("sanaa-api");
        assertThat(JwtClaims.AUDIENCE_PLAYBACK).isEqualTo("sanaa-playback");
    }
}
