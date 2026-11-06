package com.sanaa.common.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import com.sanaa.common.security.JwtTenantFilter;
import com.sanaa.common.tenant.TenantIdentifierResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates the reactive gateway: common is on the classpath but servlet, Security and Hibernate
 * APIs are not. The auto-configuration must back off instead of failing the context.
 */
class CommonAutoConfigurationReactiveTest {

    private final ReactiveWebApplicationContextRunner contextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            TenantAutoConfiguration.class,
                            JwtTenantFilterAutoConfiguration.class));

    @Test
    void reactiveContextStartsWithNoServletOrHibernateBeans() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        "org.hibernate",
                        "jakarta.servlet",
                        "org.springframework.web",
                        "org.springframework.security"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TenantIdentifierResolver.class);
                    assertThat(context).doesNotHaveBean(JwtTenantFilter.class);
                });
    }
}
