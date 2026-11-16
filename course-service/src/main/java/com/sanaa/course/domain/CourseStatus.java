package com.sanaa.course.domain;

public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    /** Set only by platform moderation. The publisher side is read-only in this status. */
    SUSPENDED
}
