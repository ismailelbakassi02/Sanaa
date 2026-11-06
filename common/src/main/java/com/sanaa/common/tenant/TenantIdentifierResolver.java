package com.sanaa.common.tenant;

import java.util.Map;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

/**
 * Supplies the tenant id to Hibernate's {@code @TenantId} discrimination.
 *
 * <p>WHY fail-closed: when no tenant was set, returning {@code null} would either make Hibernate
 * skip the discriminator or throw at an unpredictable point. Returning the nil UUID makes every
 * read match nothing and makes every insert violate the database CHECK constraint, so a missing
 * tenant can never silently read or write another tenant's data.
 *
 * <p>This class also customizes Boot's Hibernate properties so the resolver is installed without
 * every service having to repeat the {@code hibernate.tenant_identifier_resolver} property.
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getOrNil();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
