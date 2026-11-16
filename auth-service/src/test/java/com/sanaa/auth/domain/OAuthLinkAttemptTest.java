package com.sanaa.auth.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthLinkAttemptTest {

    @Test
    void expiresAndCanOnlyBeConsumedOnce() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        OAuthLinkAttempt attempt = new OAuthLinkAttempt("a".repeat(64), 1,
                IdentityProvider.GOOGLE, "subject", "verified@example.com", now.plusSeconds(60));

        assertThat(attempt.isUsableAt(now)).isTrue();
        assertThat(attempt.isUsableAt(now.plusSeconds(61))).isFalse();
        attempt.consume(now.plusSeconds(1));
        assertThat(attempt.isUsableAt(now.plusSeconds(2))).isFalse();
    }
}
