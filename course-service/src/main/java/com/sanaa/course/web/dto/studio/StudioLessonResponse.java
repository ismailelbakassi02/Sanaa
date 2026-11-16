package com.sanaa.course.web.dto.studio;

import com.sanaa.course.domain.Lesson;

public record StudioLessonResponse(
        long id,
        long moduleId,
        String title,
        String description,
        int position,
        int durationSeconds,
        String mediaStatus,
        boolean freePreview) {

    public static StudioLessonResponse from(Lesson lesson) {
        return new StudioLessonResponse(
                lesson.getId(),
                lesson.getModuleId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getPosition(),
                lesson.getDurationSeconds(),
                lesson.getMediaStatus().name(),
                lesson.isFreePreview());
    }
}
