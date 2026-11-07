package com.sanaa.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform bootstrap settings. The password has no usable default: it must come from the
 * environment outside the dev profile.
 */
@ConfigurationProperties(prefix = "sanaa.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String tenantSlug,
        String tenantName,
        String adminEmail,
        String adminPassword,
        String adminName) {
}
