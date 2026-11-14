package com.sanaa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A publisher membership: the one row that gives a user a role inside a publisher.
 *
 * <p>WHY {@code @TenantId}: memberships are publisher-private data. Hibernate fills the tenant from
 * {@link com.sanaa.common.tenant.TenantContext} on insert and filters every query by it, so a
 * studio can never list or change another studio's team. Login and public pages read the same table
 * through the read-only {@code crosstenant} view, because they run before a tenant is known.
 */
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Membership() {
    }

    public Membership(Long userId, MembershipRole role) {
        this.userId = userId;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeStatus(MembershipStatus status) {
        this.status = status;
    }

    public void changeRole(MembershipRole role) {
        this.role = role;
    }
}
