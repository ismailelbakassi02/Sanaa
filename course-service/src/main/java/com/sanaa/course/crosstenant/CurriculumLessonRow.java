package com.sanaa.course.crosstenant;

public record CurriculumLessonRow(
        long id,
        long moduleId,
        String title,
        String description,
        int position,
        int durationSeconds,
        boolean freePreview,
        boolean available) {
}
