package com.sanaa.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.UserRepository;

/**
 * Changes the password of the authenticated user.
 *
 * <p>Every refresh token of the user is revoked, not just the presented one: a password change is a
 * credential reset, so any other device must log in again. The controller then sets a brand new
 * refresh cookie, so the current tab keeps working while the flag is cleared.
 */
@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordVerifier passwordVerifier;
    private final ClaimsService claimsService;

    public PasswordChangeService(UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 PasswordVerifier passwordVerifier,
                                 ClaimsService claimsService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordVerifier = passwordVerifier;
        this.claimsService = claimsService;
    }

    @Transactional
    public AuthenticatedUser change(long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(UnauthenticatedException::new);

        if (!passwordVerifier.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.validate(newPassword);

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), java.time.Instant.now());
        return claimsService.resolve(user);
    }
}
