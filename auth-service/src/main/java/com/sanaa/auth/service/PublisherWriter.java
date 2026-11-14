package com.sanaa.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Membership;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.ConflictException;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.metrics.AuthMetrics;
import com.sanaa.auth.outbox.OutboxWriter;
import com.sanaa.auth.repository.MembershipRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.CreatePublisherRequest;
import com.sanaa.auth.web.dto.PublisherProfileResponse;
import com.sanaa.auth.web.dto.UpdatePublisherRequest;

/**
 * Writes a publisher and its first membership.
 *
 * <p>WHY this bean is separate from {@link PublisherService}: the facade generates the tenant id and
 * calls this method inside {@code TenantContext.callAs(newTenantId)}. The transaction must open
 * after the context is set, otherwise {@code @TenantId} would fill the fail-closed nil tenant.
 *
 * <p>The tenant, the membership, the optional owner account and the outbox row commit together. A
 * slug collision is translated into 409 so ten parallel calls produce exactly one 201.
 */
@Service
public class PublisherWriter {

    static final String DEFAULT_PRIMARY_COLOR = "#165DFF";

    /**
     * Data of an owner account created by an anonymous publisher request. Passed as a value object
     * so the writer does not need to read the request twice.
     */
    public record OwnerAccount(String email, String fullName, String rawPassword) {
    }

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outboxWriter;
    private final AuthMetrics metrics;

    public PublisherWriter(TenantRepository tenantRepository, UserRepository userRepository,
                           MembershipRepository membershipRepository,
                           PasswordEncoder passwordEncoder, OutboxWriter outboxWriter,
                           AuthMetrics metrics) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxWriter = outboxWriter;
        this.metrics = metrics;
    }

    @Transactional
    public Tenant create(String tenantId, String slug, CreatePublisherRequest request,
                         Long existingOwnerId, OwnerAccount newOwner) {
        String color = request.primaryColor() == null ? DEFAULT_PRIMARY_COLOR
                : request.primaryColor();
        String contactEmail = request.contactEmail() == null ? null
                : EmailNormalizer.normalize(request.contactEmail());

        Tenant tenant = new Tenant(tenantId, slug, request.publisherName().trim(),
                request.publisherType(), TenantStatus.ACTIVE, color, contactEmail);
        try {
            tenantRepository.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("This slug is already taken.");
        }

        long ownerId;
        if (existingOwnerId != null) {
            ownerId = existingOwnerId;
        } else {
            User owner = new User(newOwner.email(),
                    passwordEncoder.encode(newOwner.rawPassword()), newOwner.fullName().trim(),
                    PlatformRole.USER);
            try {
                userRepository.saveAndFlush(owner);
            } catch (DataIntegrityViolationException exception) {
                throw new EmailAlreadyUsedException();
            }
            ownerId = owner.getId();
            metrics.userRegistered();
        }

        membershipRepository.saveAndFlush(new Membership(ownerId, MembershipRole.TENANT_ADMIN));
        outboxWriter.publisherUpserted(tenant);
        metrics.publisherCreated();
        return tenant;
    }

    /**
     * Replaces the publisher profile and appends the upsert event in the same transaction, so a
     * committed profile change always reaches course-service and notification-service.
     */
    @Transactional
    public PublisherProfileResponse updatePublisher(String tenantId, UpdatePublisherRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Publisher not found."));

        tenant.updateProfile(request.name().trim(), trimToNull(request.headline()),
                trimToNull(request.description()), UrlRules.validate(request.websiteUrl()),
                UrlRules.validate(request.instagramUrl()), UrlRules.validate(request.linkedinUrl()),
                UrlRules.validate(request.youtubeUrl()), request.primaryColor(),
                request.contactEmail() == null ? null
                        : EmailNormalizer.normalize(request.contactEmail()));
        tenantRepository.saveAndFlush(tenant);
        outboxWriter.publisherUpserted(tenant);
        return toProfile(tenant);
    }

    public static PublisherProfileResponse toProfile(Tenant tenant) {
        return new PublisherProfileResponse(tenant.getSlug(), tenant.getName(), tenant.getType(),
                tenant.getStatus(), tenant.getHeadline(), tenant.getDescription(),
                tenant.getWebsiteUrl(), tenant.getInstagramUrl(), tenant.getLinkedinUrl(),
                tenant.getYoutubeUrl(), tenant.getLogoImageId(), tenant.getPrimaryColor(),
                tenant.getContactEmail());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
