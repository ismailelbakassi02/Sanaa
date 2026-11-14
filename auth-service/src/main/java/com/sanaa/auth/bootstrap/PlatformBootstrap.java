package com.sanaa.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sanaa.auth.config.BootstrapProperties;

/**
 * Idempotent platform bootstrap.
 *
 * <p>WHY the example password guard: {@code .env.example} ships a placeholder that must never reach
 * a non-dev environment. Failing startup is safer than silently creating a known-password super
 * admin. In dev, an empty password simply skips the admin instead of failing the local run.
 */
@Component
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);
    private static final String EXAMPLE_ADMIN_PASSWORD = "change-me-admin";

    private final BootstrapProperties properties;
    private final BootstrapService bootstrapService;
    private final Environment environment;

    public PlatformBootstrap(BootstrapProperties properties, BootstrapService bootstrapService,
                             Environment environment) {
        this.properties = properties;
        this.bootstrapService = bootstrapService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Platform bootstrap is disabled");
            return;
        }

        boolean dev = environment.matchesProfiles("dev");
        String password = properties.adminPassword();
        if (!StringUtils.hasText(password)) {
            if (dev) {
                log.warn("PLATFORM_ADMIN_PASSWORD is empty; skipping platform admin in dev");
                return;
            }
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_PASSWORD must be set outside the dev profile");
        }
        if (!dev && EXAMPLE_ADMIN_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_PASSWORD still uses the .env.example placeholder");
        }
        if (!StringUtils.hasText(properties.adminEmail())
                || !StringUtils.hasText(properties.adminName())) {
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_NAME must be configured");
        }

        bootstrapService.ensureSuperAdmin(properties);
        log.info("Platform SUPER_ADMIN account '{}' is ready", properties.adminEmail());
    }
}
