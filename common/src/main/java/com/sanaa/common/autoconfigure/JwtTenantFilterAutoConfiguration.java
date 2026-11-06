package com.sanaa.common.autoconfigure;

import jakarta.servlet.Filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sanaa.common.security.JwtTenantFilter;

/**
 * Servlet-only tenant filter wiring.
 *
 * <p>WHY the disabled {@link FilterRegistrationBean}: Boot registers every {@link Filter} bean as a
 * plain servlet filter. That would run the filter before Spring Security has authenticated the
 * request, and would also run it a second time when a service adds it to the Security chain.
 * Disabling the registration keeps the bean available for {@link JwtTenantFilter#addTo} without
 * double execution. The whole configuration is conditional on a servlet web application, so the
 * reactive gateway never instantiates servlet or Security filter classes.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OncePerRequestFilter.class, JwtAuthenticationToken.class, HttpSecurity.class})
public class JwtTenantFilterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTenantFilter jwtTenantFilter() {
        return new JwtTenantFilter();
    }

    @Bean
    public FilterRegistrationBean<JwtTenantFilter> jwtTenantFilterRegistration(JwtTenantFilter filter) {
        FilterRegistrationBean<JwtTenantFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
