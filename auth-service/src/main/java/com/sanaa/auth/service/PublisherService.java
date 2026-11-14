package com.sanaa.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.ConflictException;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.CreatePublisherRequest;
import com.sanaa.auth.web.dto.CreatedPublisherResponse;
import com.sanaa.auth.web.dto.PublisherProfileResponse;
import com.sanaa.auth.web.dto.SlugAvailabilityResponse;
import com.sanaa.auth.web.dto.UpdatePublisherRequest;
import com.sanaa.common.tenant.TenantContext;

/**
 * Publisher creation and slug check.
 *
 * <p>WHY creation is not transactional here: a new tenant id must be generated, the id must become
 * the Hibernate tenant, and only then may a transaction open. {@link PublisherWriter} does the
 * actual write inside {@link TenantContext#callAs}. Everything commits in that one transaction.
 */
@Service
public class PublisherService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipViewRepository membershipViewRepository;
    private final PublisherWriter publisherWriter;

    public PublisherService(TenantRepository tenantRepository, UserRepository userRepository,
                            MembershipViewRepository membershipViewRepository,
                            PublisherWriter publisherWriter) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipViewRepository = membershipViewRepository;
        this.publisherWriter = publisherWriter;
    }

    public CreatedPublisherResponse create(CreatePublisherRequest request, Jwt jwt) {
        String slug = SlugRules.requireValid(request.slug());

        Long existingOwnerId = null;
        PublisherWriter.OwnerAccount newOwner = null;
        if (jwt == null) {
            newOwner = requireAnonymousOwner(request);
        } else {
            existingOwnerId = requireLoggedInLearner(jwt, request);
        }

        if (tenantRepository.existsBySlug(slug)) {
            throw new ConflictException("This slug is already taken.");
        }

        String tenantId = UUID.randomUUID().toString();
        PublisherWriter.OwnerAccount owner = newOwner;
        Long ownerId = existingOwnerId;
        Tenant tenant = TenantContext.callAs(tenantId,
                () -> publisherWriter.create(tenantId, slug, request, ownerId, owner));

        return new CreatedPublisherResponse(tenant.getSlug(), tenant.getName(), tenant.getType());
    }

    /**
     * The owner fields are only meaningful for an anonymous call. Silently ignoring them for a
     * logged-in caller would hide a client bug and could surprise the user with a second account.
     */
    private PublisherWriter.OwnerAccount requireAnonymousOwner(CreatePublisherRequest request) {
        if (!org.springframework.util.StringUtils.hasText(request.ownerFullName())
                || !org.springframework.util.StringUtils.hasText(request.ownerEmail())
                || !org.springframework.util.StringUtils.hasText(request.password())) {
            throw new BadRequestException(
                    "Owner name, email and password are required when creating a publisher "
                            + "without a session.");
        }
        PasswordPolicy.validate(request.password());
        String email = EmailNormalizer.normalize(request.ownerEmail());
        if (userRepository.existsByEmail(email)) {
            throw new com.sanaa.auth.error.EmailAlreadyUsedException();
        }
        return new PublisherWriter.OwnerAccount(email, request.ownerFullName(), request.password());
    }

    private long requireLoggedInLearner(Jwt jwt, CreatePublisherRequest request) {
        if (org.springframework.util.StringUtils.hasText(request.ownerFullName())
                || org.springframework.util.StringUtils.hasText(request.ownerEmail())
                || org.springframework.util.StringUtils.hasText(request.password())) {
            throw new BadRequestException(
                    "Owner fields must not be sent when creating a publisher with a session.");
        }

        long userId = AuthFacade.userId(jwt);
        User user = userRepository.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(UnauthenticatedException::new);
        if (membershipViewRepository.findByUserId(userId).isPresent()) {
            throw new ConflictException("This account already belongs to a publisher.");
        }
        return user.getId();
    }

    public SlugAvailabilityResponse slugAvailable(String slug) {
        String normalized = SlugRules.normalize(slug);
        if (normalized == null || !SlugRules.isValid(normalized)
                || SlugRules.isReserved(normalized)) {
            return new SlugAvailabilityResponse(slug, false);
        }
        return new SlugAvailabilityResponse(normalized, !tenantRepository.existsBySlug(normalized));
    }

    public PublisherProfileResponse updatePublisher(String tenantId, UpdatePublisherRequest request) {
        return publisherWriter.updatePublisher(tenantId, request);
    }

    public Optional<Tenant> findActiveBySlug(String slug) {
        return tenantRepository.findBySlug(SlugRules.normalize(slug))
                .filter(tenant -> tenant.getStatus() == com.sanaa.auth.domain.TenantStatus.ACTIVE);
    }
}
