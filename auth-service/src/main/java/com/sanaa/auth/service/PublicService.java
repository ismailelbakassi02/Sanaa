package com.sanaa.auth.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.crosstenant.MembershipView;
import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.domain.MembershipStatus;
import com.sanaa.auth.domain.Profile;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.PublicInstructorResponse;
import com.sanaa.auth.web.dto.PublicPublisherResponse;

/**
 * Anonymous public publisher and instructor pages.
 *
 * <p>These are the documented public cross-tenant read model: no tenant context, only ACTIVE
 * publishers and ACTIVE memberships, and never an email (private or contact), a password hash or a
 * media path. A suspended publisher and a disabled member are simply 404.
 */
@Service
public class PublicService {

    private final TenantRepository tenantRepository;
    private final MembershipViewRepository membershipViewRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public PublicService(TenantRepository tenantRepository,
                         MembershipViewRepository membershipViewRepository,
                         UserRepository userRepository, ProfileRepository profileRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipViewRepository = membershipViewRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public PublicPublisherResponse publisher(String slug) {
        Tenant tenant = tenantRepository.findBySlug(SlugRules.normalize(slug))
                .filter(found -> found.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Publisher not found."));

        List<PublicPublisherResponse.PublicTeamMember> team = List.of();
        if (tenant.getType() == TenantType.ORGANIZATION) {
            team = team(tenant.getId());
        }

        return new PublicPublisherResponse(tenant.getSlug(), tenant.getName(),
                tenant.getType().name(), tenant.getHeadline(), tenant.getDescription(),
                tenant.getWebsiteUrl(), tenant.getInstagramUrl(), tenant.getLinkedinUrl(),
                tenant.getYoutubeUrl(), tenant.getLogoImageId(), tenant.getPrimaryColor(), team);
    }

    @Transactional(readOnly = true)
    public PublicInstructorResponse instructor(long userId) {
        User user = userRepository.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Instructor not found."));

        MembershipView membership = membershipViewRepository.findByUserId(userId)
                .filter(MembershipView::isActive)
                .orElseThrow(() -> new NotFoundException("Instructor not found."));
        Tenant tenant = tenantRepository.findById(membership.getTenantId())
                .filter(found -> found.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Instructor not found."));

        Profile profile = profileRepository.findByUserId(userId).orElseGet(() -> new Profile(userId));
        return new PublicInstructorResponse(user.getId(), user.getFullName(), profile.getHeadline(),
                profile.getBio(), profile.getWebsiteUrl(), profile.getInstagramUrl(),
                profile.getLinkedinUrl(), profile.getYoutubeUrl(), profile.getAvatarImageId(),
                tenant.getSlug(), tenant.getName());
    }

    private List<PublicPublisherResponse.PublicTeamMember> team(String tenantId) {
        List<MembershipView> memberships = membershipViewRepository
                .findAllByTenantIdAndStatusOrderByIdAsc(tenantId, MembershipStatus.ACTIVE);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(
                        memberships.stream().map(MembershipView::getUserId).toList()).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Profile> profiles = profileRepository.findAllById(
                        memberships.stream().map(MembershipView::getUserId).toList()).stream()
                .collect(Collectors.toMap(Profile::getUserId, Function.identity()));

        return memberships.stream()
                .filter(membership -> users.containsKey(membership.getUserId()))
                .map(membership -> {
                    User user = users.get(membership.getUserId());
                    Profile profile = profiles.get(membership.getUserId());
                    return new PublicPublisherResponse.PublicTeamMember(user.getId(),
                            user.getFullName(), profile == null ? null : profile.getHeadline(),
                            profile == null ? null : profile.getAvatarImageId());
                }).toList();
    }
}
