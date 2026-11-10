package com.sanaa.course.web.dto;

import com.sanaa.course.domain.Lesson;

/**
 * Lesson summary without media details. Media is not part of this API yet.
 */
public record LessonSummary(
        Long id,
        String title,
        int position,
        int durationSeconds,
        boolean freePreview) {

    public static LessonSummary from(Lesson lesson) {
        return new LessonSummary(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getPosition(),
                lesson.getDurationSeconds(),
                lesson.isFreePreview());
    }
}
