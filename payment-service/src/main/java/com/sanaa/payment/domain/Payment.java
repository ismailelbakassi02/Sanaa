package com.sanaa.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A payment submitted by a student and reviewed by a tenant administrator.
 *
 * <p>{@code tenantId} is filled from {@code TenantContext} by Hibernate and filters every read, so
 * no repository method accepts a tenant id. {@code @Version} protects review transitions: two
 * administrators approving the same payment cannot both succeed.
 *
 * <p>{@code amount}, {@code currency} and {@code courseTitle} are snapshots taken from the course
 * response at submission time. They are never read from the request, and the snapshot keeps
 * notifications independent from later course edits or deletions.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "receipt_path", nullable = false, length = 255)
    private String receiptPath;

    @Column(name = "receipt_content_type", nullable = false, length = 64)
    private String receiptContentType;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Stored generated column: non-null only for PENDING_REVIEW and APPROVED. The unique index on
     * it is the database-level guard against two open payments for the same user and course.
     * Mapped read-only so Hibernate never tries to write it.
     */
    @Column(name = "active_key", insertable = false, updatable = false, length = 80)
    private String activeKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(long userId, String userEmail, String userName, long courseId,
                   String courseTitle, BigDecimal amount, String currency,
                   PaymentMethod method, String receiptPath, String receiptContentType) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.userName = userName;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.receiptPath = receiptPath;
        this.receiptContentType = receiptContentType;
        this.status = PaymentStatus.PENDING_REVIEW;
    }

    /**
     * Moves the payment to APPROVED. Callers must check {@link #getStatus()} first; the database
     * unique index and the optimistic version are the final guards.
     */
    public void approve(long reviewerId, Instant reviewedAt) {
        this.status = PaymentStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = null;
    }

    public void reject(long reviewerId, Instant reviewedAt, String reason) {
        this.status = PaymentStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = reason;
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

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReceiptPath() {
        return receiptPath;
    }

    public String getReceiptContentType() {
        return receiptContentType;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public long getVersion() {
        return version;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
