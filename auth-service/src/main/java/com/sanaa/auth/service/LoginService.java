package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.repository.UserRepository;

/**
 * Email and password only. There is no tenant slug: users are global and the publisher is resolved
 * from the membership afterwards.
 *
 * <p>Every rejection path performs exactly one BCrypt comparison, including an unknown email and a
 * disabled account, so timing cannot be used to enumerate accounts or statuses.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordVerifier passwordVerifier;
    private final ClaimsService claimsService;

    public LoginService(UserRepository userRepository, PasswordVerifier passwordVerifier,
                        ClaimsService claimsService) {
        this.userRepository = userRepository;
        this.passwordVerifier = passwordVerifier;
        this.claimsService = claimsService;
    }

    @Transactional
    public AuthenticatedUser login(String email, String rawPassword) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            passwordVerifier.performDummyVerification(rawPassword);
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        boolean passwordMatches = passwordVerifier.matches(rawPassword, user.getPasswordHash());
        if (!passwordMatches || user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        return claimsService.resolve(user);
    }
}
