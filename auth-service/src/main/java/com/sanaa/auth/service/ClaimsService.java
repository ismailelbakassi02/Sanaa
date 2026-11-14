package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.crosstenant.MembershipView;
import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.common.security.Role;

/**
 * Resolves the claims of a global user at login, refresh and registration time.
 *
 * <p>WHY re-resolve on every refresh instead of copying the old token claims: a suspended publisher
 * or a disabled membership must downgrade the session to STUDENT at the next refresh. The database
 * is the source of truth; the access token is only a 15-minute cache of it.
 *
 * <p>Cross-tenant read: login happens before a publisher is known, so memberships are read through
 * the read-only {@link MembershipView}, never through the tenant-scoped entity. A suspended
 * publisher is filtered out here, which is exactly the documented downgrade.
 */
@Service
public class ClaimsService {

    private final MembershipViewRepository membershipViewRepository;
    private final TenantRepository tenantRepository;

    public ClaimsService(MembershipViewRepository membershipViewRepository,
                         TenantRepository tenantRepository) {
        this.membershipViewRepository = membershipViewRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser resolve(User user) {
        if (user.getPlatformRole() == PlatformRole.SUPER_ADMIN) {
            // Platform staff never act inside a publisher, even if a membership row exists.
            return new AuthenticatedUser(user.getId(), null, null, null, user.getEmail(),
                    user.getFullName(), Role.SUPER_ADMIN, user.isMustChangePassword());
        }

        Optional<MembershipView> membership = membershipViewRepository.findByUserId(user.getId());
        if (membership.isPresent() && membership.get().isActive()) {
            Optional<Tenant> tenant = tenantRepository.findById(membership.get().getTenantId())
                    .filter(found -> found.getStatus() == TenantStatus.ACTIVE);
            if (tenant.isPresent()) {
                Role role = Role.valueOf(membership.get().getRole().name());
                return new AuthenticatedUser(user.getId(), tenant.get().getId(),
                        tenant.get().getSlug(), tenant.get().getType(), user.getEmail(),
                        user.getFullName(), role, user.isMustChangePassword());
            }
        }

        // No membership, disabled membership or suspended publisher: a plain learner.
        return new AuthenticatedUser(user.getId(), null, null, null, user.getEmail(),
                user.getFullName(), Role.STUDENT, user.isMustChangePassword());
    }
}
