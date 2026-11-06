package com.sanaa.common.autoconfigure;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import com.sanaa.common.tenant.TenantIdentifierResolver;

/**
 * Wires the Hibernate tenant resolver only when Hibernate and Boot's Hibernate support are present.
 */
@AutoConfiguration
@ConditionalOnClass({CurrentTenantIdentifierResolver.class, HibernatePropertiesCustomizer.class})
public class TenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }
}
