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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * A student's access to a course.
 *
 * <p>Enrollments are created by the payment consumer, by free enrollment in the public API and,
 * later, by an admin grant. The row lives in the tenant of the course, never in the learner's
 * (a learner has no tenant). {@code userName} and {@code userEmail} are snapshots so the studio
 * student list and the public review page do not need to call auth-service.
 *
 * <p>The unique index on {@code (tenant_id, user_id, course_id)} is the final duplicate guard.
 */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "source_payment_id")
    private Long sourcePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private EnrollmentSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    protected Enrollment() {
    }

    public Enrollment(Long userId, String userName, String userEmail, Long courseId,
                      Long sourcePaymentId, EnrollmentSource source, EnrollmentStatus status,
                      Instant enrolledAt) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.courseId = courseId;
        this.sourcePaymentId = sourcePaymentId;
        this.source = source;
        this.status = status;
        this.enrolledAt = enrolledAt;
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

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getSourcePaymentId() {
        return sourcePaymentId;
    }

    public EnrollmentSource getSource() {
        return source;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }
}
