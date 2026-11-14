package com.sanaa.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform bootstrap settings. There is no platform tenant any more: the bootstrap creates one
 * global user with {@code platform_role = SUPER_ADMIN}. The password has no usable default and must
 * come from the environment outside the dev profile.
 */
@ConfigurationProperties(prefix = "sanaa.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String adminEmail,
        String adminPassword,
        String adminName) {
}
