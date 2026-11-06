package com.sanaa.common.tenant;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

class TenantIdentifierResolverTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";

    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void returnsNilTenantWhenContextIsUnset() {
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TenantContext.NIL_TENANT);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("00000000-0000-0000-0000-000000000000");
    }

    @Test
    void returnsCurrentTenantWhenSet() {
        TenantContext.set(TENANT_A);

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TENANT_A);
    }

    @Test
    void validatesExistingSessions() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }

    @Test
    void registersItselfAsHibernateProperty() {
        Map<String, Object> properties = new HashMap<>();

        resolver.customize(properties);

        assertThat(properties).containsEntry(MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
