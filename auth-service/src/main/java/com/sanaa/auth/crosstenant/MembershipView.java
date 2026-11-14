package com.sanaa.auth.crosstenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.MembershipStatus;

/**
 * Read-only view of a membership without {@code @TenantId}.
 *
 * <p>WHY: two flows need to know a user's publisher before any publisher is known. Login and refresh
 * resolve the claims of the caller, and the public publisher/instructor pages read the team of an
 * active publisher. Both are documented cross-tenant read models (architecture section 5.3), not a
 * way around isolation: this class cannot write, and every mutation still goes through the
 * tenant-scoped {@link com.sanaa.auth.domain.Membership}.
 */
@Entity
@Table(name = "memberships")
@Immutable
public class MembershipView {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    protected MembershipView() {
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }
}
