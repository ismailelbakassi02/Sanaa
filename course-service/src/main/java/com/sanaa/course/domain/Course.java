package com.sanaa.course.domain;

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
 * A tenant-owned course. Hibernate fills {@code tenantId} from {@code TenantContext} and filters
 * every read by it, so no repository method accepts a tenant id.
 *
 * <p>{@code @Version} protects concurrent edits: every update must present the version the client
 * last read. {@code instructorId} is the JWT subject of the creator and is never taken from input.
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CourseStatus status;

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
    }

    public Course(String title, String description, BigDecimal price, String currency,
                  Long instructorId) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.instructorId = instructorId;
        this.status = CourseStatus.DRAFT;
    }

    public void updateDetails(String title, String description, BigDecimal price, String currency) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.currency = currency;
    }

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public Long getInstructorId() {
        return instructorId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
