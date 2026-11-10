package com.sanaa.course.web.dto;

import com.sanaa.course.domain.Lesson;

public record LessonResponse(
        Long id,
        Long courseId,
        Long moduleId,
        String title,
        int position,
        int durationSeconds,
        boolean freePreview) {

    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getModuleId(),
                lesson.getTitle(),
                lesson.getPosition(),
                lesson.getDurationSeconds(),
                lesson.isFreePreview());
    }
}
