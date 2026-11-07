package com.sanaa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A tenant is platform data and is deliberately not tenant-scoped: login and registration have to
 * resolve a tenant by slug before a tenant context can exist.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String id, String slug, String name, TenantStatus status, String primaryColor,
                  String logoUrl) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.status = status;
        this.primaryColor = primaryColor;
        this.logoUrl = logoUrl;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }
}
