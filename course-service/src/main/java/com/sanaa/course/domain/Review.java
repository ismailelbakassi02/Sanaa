package com.sanaa.course.domain;

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
 * A learner review, owned by the tenant of the course. The learner has no tenant, so every write
 * happens inside {@code TenantContext.callAs(courseTenant, ...)} after a trusted course lookup.
 *
 * <p>{@code userName} is a snapshot shown as "First name L." on the public page. A publisher may
 * reply once; {@code repliedAt} makes the "once" rule visible without a second table.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 120)
    private String userName;

    // The column is TINYINT (1..5); the explicit type code keeps ddl-auto=validate in agreement
    // with the Flyway schema while the API keeps working with a plain int.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReviewStatus status;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "publisher_reply", length = 1000)
    private String publisherReply;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    public Review(Long courseId, Long userId, String userName, int rating, String comment) {
        this.courseId = courseId;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
        this.status = ReviewStatus.VISIBLE;
    }

    public void edit(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    /** A reply is accepted once; the service checks {@link #getRepliedAt()} before calling this. */
    public void reply(String reply, Instant repliedAt) {
        this.publisherReply = reply;
        this.repliedAt = repliedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public String getPublisherReply() {
        return publisherReply;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
