package com.sanaa.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Membership;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.MembershipStatus;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.Profile;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.ConflictException;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.repository.MembershipRepository;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.AddTeamMemberRequest;
import com.sanaa.auth.web.dto.TeamMemberResponse;

/**
 * Publisher team management, TENANT_ADMIN only.
 *
 * <p>The tenant always comes from the validated JWT (through {@code TenantContext} and the tenant id
 * passed by the controller). No request field can choose another publisher. The "last active admin"
 * rule uses a pessimistic lock on all active admin rows, so two concurrent disables cannot both
 * succeed.
 */
@Service
public class TeamService {

    public static final int MAX_TEAM_PAGE = 50;

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public TeamService(TenantRepository tenantRepository, MembershipRepository membershipRepository,
                       UserRepository userRepository, ProfileRepository profileRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> list(String tenantId) {
        requireOrganization(tenantId);
        List<Membership> memberships = membershipRepository
                .findAllByOrderByIdAsc(PageRequest.of(0, MAX_TEAM_PAGE)).getContent();
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, User> users = userRepository.findAllById(
                        memberships.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Profile> profiles = profileRepository.findAllById(
                        memberships.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(Profile::getUserId, Function.identity()));

        return memberships.stream().map(membership -> {
            User user = users.get(membership.getUserId());
            Profile profile = profiles.get(membership.getUserId());
            return toResponse(membership, user, profile);
        }).toList();
    }

    @Transactional
    public TeamMemberResponse add(String tenantId, AddTeamMemberRequest request) {
        requireOrganization(tenantId);
        PasswordPolicy.validate(request.temporaryPassword());
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(email, passwordEncoder.encode(request.temporaryPassword()),
                request.fullName().trim(), PlatformRole.USER);
        user.requirePasswordChange();
        try {
            // Flush now so a concurrent duplicate is a 409 at this point, not a 500 at commit.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }

        Membership membership;
        try {
            membership = membershipRepository.saveAndFlush(
                    new Membership(user.getId(), request.role()));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("This account already belongs to a publisher.");
        }
        return toResponse(membership, user, null);
    }

    @Transactional
    public TeamMemberResponse updateStatus(String tenantId, long targetUserId,
                                           MembershipStatus status, long actorUserId) {
        requireOrganization(tenantId);
        Membership membership = membershipRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));

        if (status == MembershipStatus.DISABLED) {
            if (targetUserId == actorUserId) {
                throw new ConflictException("You cannot disable your own account.");
            }
            if (membership.isActive() && membership.getRole() == MembershipRole.TENANT_ADMIN) {
                // Locks every active admin row in this tenant. The second concurrent request blocks
                // here, then sees the first one's committed change and refuses to remove the last.
                List<Membership> activeAdmins = membershipRepository.lockByRoleAndStatus(
                        MembershipRole.TENANT_ADMIN, MembershipStatus.ACTIVE);
                if (activeAdmins.size() <= 1) {
                    throw new ConflictException(
                            "The publisher must keep at least one active admin.");
                }
            }
        }

        membership.changeStatus(status);
        membershipRepository.saveAndFlush(membership);
        if (status == MembershipStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(targetUserId, Instant.now());
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));
        Profile profile = profileRepository.findByUserId(targetUserId).orElse(null);
        return toResponse(membership, user, profile);
    }

    @Transactional
    public void resetPassword(String tenantId, long targetUserId, String temporaryPassword) {
        requireOrganization(tenantId);
        membershipRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));
        PasswordPolicy.validate(temporaryPassword);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));
        user.changePassword(passwordEncoder.encode(temporaryPassword));
        user.requirePasswordChange();
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(targetUserId, Instant.now());
    }

    private void requireOrganization(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Publisher not found."));
        if (tenant.getType() != TenantType.ORGANIZATION) {
            throw new ConflictException("Only an organization publisher can manage a team.");
        }
    }

    private static TeamMemberResponse toResponse(Membership membership, User user,
                                                 Profile profile) {
        return new TeamMemberResponse(user.getId(), user.getEmail(), user.getFullName(),
                membership.getRole().name(), membership.getStatus().name(),
                profile == null ? null : profile.getHeadline(),
                profile == null ? null : profile.getAvatarImageId(),
                user.isMustChangePassword(), membership.getCreatedAt());
    }
}
