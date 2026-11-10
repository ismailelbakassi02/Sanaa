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
 * A lesson inside a module.
 *
 * <p>{@code courseId} is deliberately denormalized from the module. Entitlement checks and the
 * streaming endpoints need the course for a lesson id; keeping it here avoids a join and keeps the
 * tenant-leading index useful. The service always derives the value from the module, never from
 * client input.
 */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", nullable = false, length = 16)
    private MediaStatus mediaStatus;

    @Column(name = "free_preview", nullable = false)
    private boolean freePreview;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lesson() {
    }

    public Lesson(Long courseId, Long moduleId, String title, int position, int durationSeconds,
                  boolean freePreview) {
        this.courseId = courseId;
        this.moduleId = moduleId;
        this.title = title;
        this.position = position;
        this.durationSeconds = durationSeconds;
        this.mediaStatus = MediaStatus.NONE;
        this.freePreview = freePreview;
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

    public Long getModuleId() {
        return moduleId;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public MediaStatus getMediaStatus() {
        return mediaStatus;
    }

    public boolean isFreePreview() {
        return freePreview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
