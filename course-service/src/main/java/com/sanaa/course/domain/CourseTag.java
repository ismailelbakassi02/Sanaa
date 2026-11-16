package com.sanaa.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * A normalized tag on a course. Tags are tenant-owned and the service caps a course at ten of them.
 * {@code tag} is stored lower case so the public exact-tag filter is collation independent.
 */
@Entity
@Table(name = "course_tags")
public class CourseTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "tag", nullable = false, length = 30)
    private String tag;

    protected CourseTag() {
    }

    public CourseTag(Long courseId, String tag) {
        this.courseId = courseId;
        this.tag = tag;
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getTag() {
        return tag;
    }
}
