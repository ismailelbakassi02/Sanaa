package com.sanaa.auth.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.sanaa.auth.config.BootstrapProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformBootstrapTest {

    @Test
    void rejectsTheExamplePasswordOutsideDev() {
        BootstrapProperties properties = properties("change-me-admin");
        BootstrapService bootstrapService = mock(BootstrapService.class);
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("dev")).thenReturn(false);

        PlatformBootstrap runner = new PlatformBootstrap(properties, bootstrapService, environment);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
        verifyNoInteractions(bootstrapService);
    }

    @Test
    void rejectsAnEmptyPasswordOutsideDev() {
        BootstrapProperties properties = properties("");
        BootstrapService bootstrapService = mock(BootstrapService.class);
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("dev")).thenReturn(false);

        PlatformBootstrap runner = new PlatformBootstrap(properties, bootstrapService, environment);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
        verifyNoInteractions(bootstrapService);
    }

    @Test
    void allowsAnEmptyPasswordInDevBySkippingTheAdmin() {
        BootstrapProperties properties = properties("");
        BootstrapService bootstrapService = mock(BootstrapService.class);
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("dev")).thenReturn(true);

        PlatformBootstrap runner = new PlatformBootstrap(properties, bootstrapService, environment);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(bootstrapService);
    }

    private static BootstrapProperties properties(String adminPassword) {
        return new BootstrapProperties(true, "admin@platform.local", adminPassword,
                "Platform Admin");
    }
}
