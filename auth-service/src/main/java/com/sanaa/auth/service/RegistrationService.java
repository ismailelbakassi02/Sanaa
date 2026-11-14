package com.sanaa.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.metrics.AuthMetrics;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.RegisterRequest;

/**
 * Public learner registration. The account is global: no tenant row and no membership are created,
 * because a learner can buy from any publisher.
 *
 * <p>The duplicate-email race is closed by the unique index, and the resulting integrity violation
 * is translated into the same 409 as the pre-check. A concurrent duplicate must never become a 500.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClaimsService claimsService;
    private final AuthMetrics metrics;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               ClaimsService claimsService, AuthMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.claimsService = claimsService;
        this.metrics = metrics;
    }

    @Transactional
    public AuthenticatedUser register(RegisterRequest request) {
        PasswordPolicy.validate(request.password());
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), PlatformRole.USER);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }

        metrics.userRegistered();
        return claimsService.resolve(user);
    }
}
