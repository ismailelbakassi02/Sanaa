package com.sanaa.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.RegisterRequest;
import com.sanaa.common.security.Role;

/**
 * Runs inside a tenant context supplied by {@link AuthFacade}. The transaction must therefore be
 * opened by this bean, after the context is set.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedUser register(Tenant tenant, RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.STUDENT);
        try {
            // Flush here so a concurrent duplicate is translated into the API conflict now instead
            // of surfacing as a generic persistence error at commit time.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }

        return new AuthenticatedUser(user.getId(), tenant.getId(), tenant.getSlug(), user.getEmail(),
                user.getFullName(), user.getRole());
    }
}
