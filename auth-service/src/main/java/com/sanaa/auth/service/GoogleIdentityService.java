package com.sanaa.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sanaa.auth.config.GoogleOAuthProperties;
import com.sanaa.auth.domain.ExternalIdentity;
import com.sanaa.auth.domain.IdentityProvider;
import com.sanaa.auth.domain.OAuthLinkAttempt;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.ConflictException;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.metrics.AuthMetrics;
import com.sanaa.auth.repository.ExternalIdentityRepository;
import com.sanaa.auth.repository.OAuthLinkAttemptRepository;
import com.sanaa.auth.repository.UserRepository;

/**
 * Maps a verified Google subject to a global SANAA account.
 *
 * <p>An existing password account is never linked by email alone: the callback creates a short-lived
 * opaque ticket, and the owner must confirm the existing password. A new verified email creates a
 * passwordless learner that can set a local password later.
 */
@Service
public class GoogleIdentityService {

    private static final IdentityProvider PROVIDER = IdentityProvider.GOOGLE;

    private final ExternalIdentityRepository identityRepository;
    private final OAuthLinkAttemptRepository linkAttemptRepository;
    private final UserRepository userRepository;
    private final OAuthLinkTokenCodec tokenCodec;
    private final PasswordVerifier passwordVerifier;
    private final ClaimsService claimsService;
    private final GoogleOAuthProperties properties;
    private final AuthMetrics metrics;
    private final Clock clock;

    public GoogleIdentityService(ExternalIdentityRepository identityRepository,
                                 OAuthLinkAttemptRepository linkAttemptRepository,
                                 UserRepository userRepository,
                                 OAuthLinkTokenCodec tokenCodec,
                                 PasswordVerifier passwordVerifier,
                                 ClaimsService claimsService,
                                 GoogleOAuthProperties properties,
                                 AuthMetrics metrics,
                                 Clock clock) {
        this.identityRepository = identityRepository;
        this.linkAttemptRepository = linkAttemptRepository;
        this.userRepository = userRepository;
        this.tokenCodec = tokenCodec;
        this.passwordVerifier = passwordVerifier;
        this.claimsService = claimsService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public GoogleLoginOutcome begin(GoogleProfile profile) {
        if (!profile.emailVerified()) {
            throw new UnauthenticatedException();
        }

        Optional<ExternalIdentity> linked = identityRepository
                .findByProviderAndProviderSubject(PROVIDER, profile.subject());
        if (linked.isPresent()) {
            User user = requireActiveUser(linked.get().getUserId());
            linked.get().updateVerifiedEmail(profile.email());
            return new GoogleLoginOutcome.Authenticated(claimsService.resolve(user));
        }

        Optional<User> existing = userRepository.findByEmail(profile.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getStatus() != UserStatus.ACTIVE || !user.hasPassword()) {
                throw new UnauthenticatedException();
            }
            linkAttemptRepository.deleteByExpiresAtBefore(clock.instant());
            String rawTicket = tokenCodec.generate();
            Instant expiresAt = clock.instant().plus(properties.linkTicketTtl());
            linkAttemptRepository.saveAndFlush(new OAuthLinkAttempt(tokenCodec.hash(rawTicket),
                    user.getId(), PROVIDER, profile.subject(), profile.email(), expiresAt));
            return new GoogleLoginOutcome.LinkRequired(rawTicket);
        }

        User user = User.withoutPassword(profile.email(), profile.fullName());
        userRepository.saveAndFlush(user);
        identityRepository.saveAndFlush(new ExternalIdentity(user.getId(), PROVIDER,
                profile.subject(), profile.email()));
        metrics.userRegistered();
        return new GoogleLoginOutcome.Authenticated(claimsService.resolve(user));
    }

    @Transactional
    public AuthenticatedUser confirmLink(String rawTicket, String password) {
        if (!StringUtils.hasText(rawTicket) || !StringUtils.hasText(password)) {
            throw new UnauthenticatedException();
        }
        OAuthLinkAttempt attempt = linkAttemptRepository
                .findByTokenHashForUpdate(tokenCodec.hash(rawTicket))
                .filter(found -> found.isUsableAt(clock.instant()))
                .orElseThrow(UnauthenticatedException::new);
        User user = requireActiveUser(attempt.getUserId());

        if (!passwordVerifier.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Optional<ExternalIdentity> subjectIdentity = identityRepository
                .findByProviderAndProviderSubject(PROVIDER, attempt.getProviderSubject());
        if (subjectIdentity.isPresent() && !subjectIdentity.get().getUserId().equals(user.getId())) {
            throw new ConflictException("This Google identity is already linked to another account.");
        }
        Optional<ExternalIdentity> userIdentity = identityRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER);
        if (userIdentity.isPresent()
                && !userIdentity.get().getProviderSubject().equals(attempt.getProviderSubject())) {
            throw new ConflictException("This account already has a different Google identity.");
        }
        if (subjectIdentity.isEmpty() && userIdentity.isEmpty()) {
            identityRepository.saveAndFlush(new ExternalIdentity(user.getId(), PROVIDER,
                    attempt.getProviderSubject(), attempt.getProviderEmail()));
        }
        attempt.consume(clock.instant());
        return claimsService.resolve(user);
    }

    private User requireActiveUser(long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(UnauthenticatedException::new);
    }
}
