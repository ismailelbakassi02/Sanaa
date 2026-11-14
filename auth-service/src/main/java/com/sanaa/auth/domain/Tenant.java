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
 * A publisher. The tenant id is the publisher id and is platform data, deliberately not
 * tenant-scoped: public pages and login have to resolve a publisher before a tenant context exists.
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
    @Column(name = "type", nullable = false, length = 16)
    private TenantType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "headline", length = 160)
    private String headline;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "website_url", length = 200)
    private String websiteUrl;

    @Column(name = "instagram_url", length = 200)
    private String instagramUrl;

    @Column(name = "linkedin_url", length = 200)
    private String linkedinUrl;

    @Column(name = "youtube_url", length = 200)
    private String youtubeUrl;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "logo_image_id", length = 36)
    private String logoImageId;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String id, String slug, String name, TenantType type, TenantStatus status,
                  String primaryColor, String contactEmail) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.type = type;
        this.status = status;
        this.primaryColor = primaryColor;
        this.contactEmail = contactEmail;
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

    public TenantType getType() {
        return type;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getHeadline() {
        return headline;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public String getLogoImageId() {
        return logoImageId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateProfile(String name, String headline, String description, String websiteUrl,
                              String instagramUrl, String linkedinUrl, String youtubeUrl,
                              String primaryColor, String contactEmail) {
        this.name = name;
        this.headline = headline;
        this.description = description;
        this.websiteUrl = websiteUrl;
        this.instagramUrl = instagramUrl;
        this.linkedinUrl = linkedinUrl;
        this.youtubeUrl = youtubeUrl;
        this.primaryColor = primaryColor;
        this.contactEmail = contactEmail;
    }

    public void changeLogo(String logoImageId) {
        this.logoImageId = logoImageId;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
    }
}
