package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.repository.UserRepository;

/**
 * Runs inside the resolved tenant context. Every condition that can reject a login throws the same
 * exception and only after a BCrypt comparison, so callers cannot distinguish the cases.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordVerifier passwordVerifier;

    public LoginService(UserRepository userRepository, PasswordVerifier passwordVerifier) {
        this.userRepository = userRepository;
        this.passwordVerifier = passwordVerifier;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser login(Tenant tenant, String email, String rawPassword) {
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

        return new AuthenticatedUser(user.getId(), tenant.getId(), tenant.getSlug(), user.getEmail(),
                user.getFullName(), user.getRole());
    }
}
