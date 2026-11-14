package com.sanaa.auth.bootstrap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.config.BootstrapProperties;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.service.EmailNormalizer;

/**
 * Creates the platform SUPER_ADMIN as a global user.
 *
 * <p>No tenant and no membership are involved: users are global in v2, and platform staff must
 * never be able to act inside a publisher. The operation is idempotent; an existing account is left
 * untouched so a restart does not reset a password an admin already changed.
 */
@Service
public class BootstrapService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void ensureSuperAdmin(BootstrapProperties properties) {
        String email = EmailNormalizer.normalize(properties.adminEmail());
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getPlatformRole() != PlatformRole.SUPER_ADMIN) {
                // Never silently take over an existing account; an operator must resolve this.
                throw new IllegalStateException(
                        "PLATFORM_ADMIN_EMAIL belongs to a non-admin account: " + email);
            }
        });
        if (userRepository.existsByEmail(email)) {
            return;
        }

        userRepository.saveAndFlush(new User(email,
                passwordEncoder.encode(properties.adminPassword()),
                properties.adminName().trim(), PlatformRole.SUPER_ADMIN));
    }
}
