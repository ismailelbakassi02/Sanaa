package com.sanaa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Public profile of a user. Global like the user: the same profile is shown on the publisher team
 * page and the instructor page. {@code userId} is the primary key, so every user has at most one.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "headline", length = 120)
    private String headline;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "website_url", length = 200)
    private String websiteUrl;

    @Column(name = "instagram_url", length = 200)
    private String instagramUrl;

    @Column(name = "linkedin_url", length = 200)
    private String linkedinUrl;

    @Column(name = "youtube_url", length = 200)
    private String youtubeUrl;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "avatar_image_id", length = 36)
    private String avatarImageId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getBio() {
        return bio;
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

    public String getAvatarImageId() {
        return avatarImageId;
    }

    public void update(String headline, String bio, String websiteUrl, String instagramUrl,
                       String linkedinUrl, String youtubeUrl) {
        this.headline = headline;
        this.bio = bio;
        this.websiteUrl = websiteUrl;
        this.instagramUrl = instagramUrl;
        this.linkedinUrl = linkedinUrl;
        this.youtubeUrl = youtubeUrl;
    }

    public void changeAvatar(String avatarImageId) {
        this.avatarImageId = avatarImageId;
    }
}
