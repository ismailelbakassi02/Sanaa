package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.crosstenant.MembershipView;
import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.Profile;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.outbox.OutboxWriter;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.ProfileResponse;
import com.sanaa.auth.web.dto.UpdateProfileRequest;

/**
 * Own public profile, shared by learners, instructors and publisher admins.
 *
 * <p>An instructor profile edit emits {@code INSTRUCTOR_UPDATED} so course pages can refresh their
 * instructor name snapshot. The event is written in the same transaction as the profile update.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MembershipViewRepository membershipViewRepository;
    private final TenantRepository tenantRepository;
    private final OutboxWriter outboxWriter;

    public ProfileService(UserRepository userRepository, ProfileRepository profileRepository,
                          MembershipViewRepository membershipViewRepository,
                          TenantRepository tenantRepository, OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.membershipViewRepository = membershipViewRepository;
        this.tenantRepository = tenantRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(long userId) {
        User user = requireUser(userId);
        Profile profile = profileRepository.findByUserId(userId).orElseGet(() -> empty(userId));
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse update(long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        Profile profile = profileRepository.findByUserId(userId).orElseGet(() -> empty(userId));

        String fullName = request.fullName() == null || request.fullName().isBlank()
                ? user.getFullName() : request.fullName().trim();
        user.changeFullName(fullName);
        userRepository.saveAndFlush(user);

        profile.update(trimToNull(request.headline()), trimToNull(request.bio()),
                UrlRules.validate(request.websiteUrl()), UrlRules.validate(request.instagramUrl()),
                UrlRules.validate(request.linkedinUrl()), UrlRules.validate(request.youtubeUrl()));
        profileRepository.saveAndFlush(profile);

        Optional<MembershipView> membership = membershipViewRepository.findByUserId(userId);
        if (membership.isPresent() && membership.get().isActive()
                && membership.get().getRole() == MembershipRole.INSTRUCTOR) {
            Optional<Tenant> tenant = tenantRepository.findById(membership.get().getTenantId())
                    .filter(found -> found.getStatus() == TenantStatus.ACTIVE);
            tenant.ifPresent(found -> outboxWriter.instructorUpdated(userId, found.getId(), fullName));
        }

        return toResponse(user, profile);
    }

    public static ProfileResponse toResponse(User user, Profile profile) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getFullName(),
                profile.getHeadline(), profile.getBio(), profile.getWebsiteUrl(),
                profile.getInstagramUrl(), profile.getLinkedinUrl(), profile.getYoutubeUrl(),
                profile.getAvatarImageId());
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(UnauthenticatedException::new);
    }

    private static Profile empty(long userId) {
        return new Profile(userId);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
