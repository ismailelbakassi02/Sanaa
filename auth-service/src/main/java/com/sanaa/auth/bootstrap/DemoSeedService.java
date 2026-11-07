package com.sanaa.auth.bootstrap;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.service.EmailNormalizer;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;

/**
 * Idempotent demo data creation. Each call is transactional; the caller sets the tenant context
 * before {@link #ensureAccounts} runs, so {@code @TenantId} fills the user discriminator.
 */
@Service
public class DemoSeedService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoSeedService(TenantRepository tenantRepository, UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Tenant ensureTenant(String slug, String name, String primaryColor) {
        return tenantRepository.findBySlug(slug).orElseGet(() -> tenantRepository.saveAndFlush(
                new Tenant(java.util.UUID.randomUUID().toString(), slug, name,
                        TenantStatus.ACTIVE, primaryColor, null)));
    }

    @Transactional
    public void ensureAccounts(String tenantId, String slug, String rawPassword) {
        if (!tenantId.equals(TenantContext.get())) {
            throw new IllegalStateException("Demo accounts ran without the tenant context");
        }

        String label = slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1);
        ensureAccount(slug, "admin", label + " Admin", Role.TENANT_ADMIN, rawPassword);
        ensureAccount(slug, "instructor", label + " Instructor", Role.INSTRUCTOR, rawPassword);
        ensureAccount(slug, "student1", label + " Student One", Role.STUDENT, rawPassword);
        ensureAccount(slug, "student2", label + " Student Two", Role.STUDENT, rawPassword);
    }

    private void ensureAccount(String slug, String localPart, String fullName, Role role,
                               String rawPassword) {
        String email = EmailNormalizer.normalize(localPart + "@" + slug + ".local");
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(rawPassword), fullName, role));
    }
}
