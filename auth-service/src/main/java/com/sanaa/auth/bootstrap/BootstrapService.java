package com.sanaa.auth.bootstrap;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.config.BootstrapProperties;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.service.EmailNormalizer;
import com.sanaa.auth.service.TenantLookupService;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;

/**
 * Creates the global platform tenant and its SUPER_ADMIN. The tenant creation is transactional on
 * its own; the admin creation runs in a second transaction opened after the caller sets the tenant
 * context, so {@code @TenantId} can populate the discriminator.
 */
@Service
public class BootstrapService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{3,63}$");
    private static final String PLATFORM_PRIMARY_COLOR = "#165DFF";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapService(TenantRepository tenantRepository, UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Tenant ensurePlatformTenant(BootstrapProperties properties) {
        String slug = TenantLookupService.normalizeSlug(properties.tenantSlug());
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalStateException("Platform tenant slug is invalid: " + properties.tenantSlug());
        }
        if (!org.springframework.util.StringUtils.hasText(properties.tenantName())) {
            throw new IllegalStateException("Platform tenant name must be configured");
        }

        return tenantRepository.findBySlug(slug).orElseGet(() -> tenantRepository.saveAndFlush(
                new Tenant(UUID.randomUUID().toString(), slug, properties.tenantName().trim(),
                        TenantStatus.ACTIVE, PLATFORM_PRIMARY_COLOR, null)));
    }

    @Transactional
    public void ensurePlatformAdmin(String tenantId, BootstrapProperties properties) {
        // Defensive check: the caller must have set the context before this transaction opened.
        if (!tenantId.equals(TenantContext.get())) {
            throw new IllegalStateException("Platform admin bootstrap ran without the tenant context");
        }

        String email = EmailNormalizer.normalize(properties.adminEmail());
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User(email, passwordEncoder.encode(properties.adminPassword()),
                properties.adminName().trim(), Role.SUPER_ADMIN);
        userRepository.saveAndFlush(admin);
    }
}
