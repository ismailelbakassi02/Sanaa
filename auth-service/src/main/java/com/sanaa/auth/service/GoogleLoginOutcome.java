package com.sanaa.auth.service;

/** Result of a verified Google identity: a session or explicit existing-account confirmation. */
public sealed interface GoogleLoginOutcome {

    record Authenticated(AuthenticatedUser user) implements GoogleLoginOutcome {
    }

    record LinkRequired(String rawTicket) implements GoogleLoginOutcome {
    }
}
