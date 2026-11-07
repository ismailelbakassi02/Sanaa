package com.sanaa.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.CreateTenantUserRequest;
import com.sanaa.auth.web.dto.CreatedUserResponse;
import com.sanaa.common.security.Role;

/**
 * Creates an INSTRUCTOR or TENANT_ADMIN in the caller's tenant. The tenant comes from the validated
 * JWT through {@link com.sanaa.common.tenant.TenantContext}; it is never read from the request.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CreatedUserResponse createUser(CreateTenantUserRequest request) {
        if (request.role() != Role.INSTRUCTOR && request.role() != Role.TENANT_ADMIN) {
            throw new BadRequestException("Only INSTRUCTOR and TENANT_ADMIN users can be created.");
        }

        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.role());
        try {
            // Flush now so a concurrent duplicate is translated into 409 at this point instead of
            // surfacing as a generic persistence failure at commit time.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }

        return new CreatedUserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getTenantId());
    }
}
