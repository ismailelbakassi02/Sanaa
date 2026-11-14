package com.sanaa.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.crosstenant.MembershipView;
import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.domain.AdminAuditLog;
import com.sanaa.auth.domain.MembershipStatus;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.ConflictException;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.outbox.OutboxWriter;
import com.sanaa.auth.repository.AdminAuditLogRepository;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.AdminAuditEntry;
import com.sanaa.auth.web.dto.AdminPublisherSummary;
import com.sanaa.auth.web.dto.AdminUserSummary;
import com.sanaa.auth.web.dto.PageResponse;

/**
 * Platform oversight, SUPER_ADMIN only.
 *
 * <p>Every method is a normal (not read-only) transaction because it must be able to append its
 * audit row. The password hash is never mapped into a DTO.
 */
@Service
public class AdminService {

    public static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final MembershipViewRepository membershipViewRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxWriter outboxWriter;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository, TenantRepository tenantRepository,
                        MembershipViewRepository membershipViewRepository,
                        AdminAuditLogRepository adminAuditLogRepository,
                        RefreshTokenRepository refreshTokenRepository, OutboxWriter outboxWriter,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.membershipViewRepository = membershipViewRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxWriter = outboxWriter;
        this.auditService = auditService;
    }

    @Transactional
    public PageResponse<AdminUserSummary> listUsers(String query, int page, int size, Jwt jwt) {
        PageRequest pageable = page(page, size);
        Page<User> result = query == null || query.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        query.trim(), query.trim(), pageable);

        Map<Long, MembershipView> memberships = memberships(result.getContent());
        Map<String, Tenant> tenants = tenants(memberships.values());
        List<AdminUserSummary> items = result.getContent().stream()
                .map(user -> toSummary(user, memberships.get(user.getId()), tenants))
                .toList();

        auditService.record(jwt, AuditService.USER_LISTED, "USER", null, null, null);
        return page(items, result);
    }

    @Transactional
    public AdminUserSummary userDetail(long userId, Jwt jwt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        MembershipView membership = membershipViewRepository.findByUserId(userId).orElse(null);
        Map<String, Tenant> tenants = membership == null ? Map.of()
                : Map.of(membership.getTenantId(), tenantRepository.findById(membership.getTenantId())
                        .orElseThrow(() -> new NotFoundException("Publisher not found.")));

        // Personal data read: audited before the response leaves the service.
        auditService.record(jwt, AuditService.USER_READ, "USER", Long.toString(userId),
                membership == null ? null : membership.getTenantId(), null);
        return toSummary(user, membership, tenants);
    }

    @Transactional
    public AdminUserSummary updateUserStatus(long userId, UserStatus status, String reason,
                                             Jwt jwt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        if (status == UserStatus.DISABLED && userId == AuthFacade.userId(jwt)) {
            throw new ConflictException("You cannot disable your own account.");
        }

        user.changeStatus(status);
        userRepository.saveAndFlush(user);
        if (status == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        }

        MembershipView membership = membershipViewRepository.findByUserId(userId).orElse(null);
        Map<String, Tenant> tenants = membership == null ? Map.of()
                : Map.of(membership.getTenantId(), tenantRepository.findById(membership.getTenantId())
                        .orElseThrow(() -> new NotFoundException("Publisher not found.")));
        auditService.record(jwt, AuditService.USER_STATUS_CHANGED, "USER", Long.toString(userId),
                membership == null ? null : membership.getTenantId(), reason);
        return toSummary(user, membership, tenants);
    }

    @Transactional
    public PageResponse<AdminPublisherSummary> listPublishers(String query, int page, int size,
                                                              Jwt jwt) {
        PageRequest pageable = page(page, size);
        Page<Tenant> result = query == null || query.isBlank()
                ? tenantRepository.findAll(pageable)
                : tenantRepository.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(
                        query.trim(), query.trim(), pageable);

        List<AdminPublisherSummary> items = result.getContent().stream()
                .map(this::toSummary).toList();
        auditService.record(jwt, AuditService.PUBLISHER_LISTED, "PUBLISHER", null, null, null);
        return page(items, result);
    }

    @Transactional
    public AdminPublisherSummary updatePublisherStatus(String tenantId, TenantStatus status,
                                                       String reason, Jwt jwt) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Publisher not found."));

        if (status == TenantStatus.SUSPENDED) {
            tenant.suspend();
            // Members keep their access token for at most 15 minutes; their refresh sessions end now.
            refreshTokenRepository.revokeAllForTenant(tenantId, Instant.now());
        } else {
            tenant.activate();
        }
        tenantRepository.saveAndFlush(tenant);
        outboxWriter.publisherUpserted(tenant);
        auditService.record(jwt, AuditService.PUBLISHER_STATUS_CHANGED, "PUBLISHER", tenantId,
                tenantId, reason);
        return toSummary(tenant);
    }

    @Transactional
    public PageResponse<AdminAuditEntry> listAudit(String actor, String action, Instant from,
                                                   Instant to, int page, int size, Jwt jwt) {
        PageRequest pageable = PageRequest.of(page, checkedSize(size),
                Sort.by(Sort.Direction.DESC, "id"));

        Specification<AdminAuditLog> specification = (root, query, builder) ->
                builder.conjunction();
        if (actor != null && !actor.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("actorEmail"), actor.trim()));
        }
        if (action != null && !action.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("action"), action.trim()));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        Page<AdminAuditLog> result = adminAuditLogRepository.findAll(specification, pageable);
        List<AdminAuditEntry> items = result.getContent().stream()
                .map(AdminService::toEntry).toList();
        auditService.record(jwt, AuditService.AUDIT_LISTED, "AUDIT", null, null, null);
        return page(items, result);
    }

    private Map<Long, MembershipView> memberships(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        return membershipViewRepository.findAllByUserIdIn(
                        users.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(MembershipView::getUserId, Function.identity()));
    }

    private Map<String, Tenant> tenants(Iterable<MembershipView> memberships) {
        List<String> ids = new java.util.ArrayList<>();
        memberships.forEach(membership -> ids.add(membership.getTenantId()));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
    }

    private AdminUserSummary toSummary(User user, MembershipView membership,
                                       Map<String, Tenant> tenants) {
        if (membership == null) {
            return new AdminUserSummary(user.getId(), user.getEmail(), user.getFullName(),
                    user.getPlatformRole().name(), user.getStatus().name(),
                    user.isMustChangePassword(), null, null, null, user.getCreatedAt());
        }
        Tenant tenant = tenants.get(membership.getTenantId());
        return new AdminUserSummary(user.getId(), user.getEmail(), user.getFullName(),
                user.getPlatformRole().name(), user.getStatus().name(),
                user.isMustChangePassword(), membership.getTenantId(),
                tenant == null ? null : tenant.getSlug(), membership.getRole().name(),
                user.getCreatedAt());
    }

    private AdminPublisherSummary toSummary(Tenant tenant) {
        long activeMembers = membershipViewRepository.countByTenantIdAndStatus(tenant.getId(),
                MembershipStatus.ACTIVE);
        return new AdminPublisherSummary(tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getType().name(), tenant.getStatus().name(), tenant.getPrimaryColor(),
                tenant.getContactEmail(), tenant.getHeadline(), activeMembers,
                tenant.getCreatedAt());
    }

    private static AdminAuditEntry toEntry(AdminAuditLog entry) {
        return new AdminAuditEntry(entry.getId(), entry.getActorEmail(), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), entry.getTenantId(), entry.getReason(),
                entry.getCreatedAt());
    }

    private static PageRequest page(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be zero or greater.");
        }
        return PageRequest.of(page, checkedSize(size), Sort.by(Sort.Direction.DESC, "id"));
    }

    private static int checkedSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and 50.");
        }
        return size;
    }

    private static <T> PageResponse<T> page(List<T> items, Page<?> source) {
        return new PageResponse<>(items, source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
