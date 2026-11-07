package com.sanaa.auth.bootstrap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.common.tenant.TenantContext;

/**
 * Demo data for local walkthroughs. It is only registered with the "demo" profile and never
 * contains a password: the password comes from DEMO_PASSWORD, and a missing value fails startup
 * with an explicit message.
 */
@Component
@Profile("demo")
public class DemoSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    private static final List<DemoTenant> TENANTS = List.of(
            new DemoTenant("bhihi", "Bhihi Academy", "#00B42A"),
            new DemoTenant("acme", "Acme Academy", "#722ED1"));

    private final Environment environment;
    private final DemoSeedService demoSeedService;

    public DemoSeed(Environment environment, DemoSeedService demoSeedService) {
        this.environment = environment;
        this.demoSeedService = demoSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = environment.getProperty("DEMO_PASSWORD");
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "DEMO_PASSWORD must be set when the demo profile is active");
        }

        for (DemoTenant demo : TENANTS) {
            Tenant tenant = demoSeedService.ensureTenant(demo.slug(), demo.name(), demo.color());
            TenantContext.runAs(tenant.getId(),
                    () -> demoSeedService.ensureAccounts(tenant.getId(), demo.slug(), password));
        }
        log.info("Demo tenants and accounts are ready");
    }

    private record DemoTenant(String slug, String name, String color) {
    }
}
