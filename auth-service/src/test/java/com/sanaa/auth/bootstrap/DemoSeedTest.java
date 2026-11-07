package com.sanaa.auth.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoSeedTest {

    @Test
    void failsWithAClearMessageWhenThePasswordIsMissing() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("DEMO_PASSWORD")).thenReturn("  ");
        DemoSeedService demoSeedService = mock(DemoSeedService.class);

        DemoSeed seed = new DemoSeed(environment, demoSeedService);

        assertThatThrownBy(() -> seed.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");
        verifyNoInteractions(demoSeedService);
    }
}
