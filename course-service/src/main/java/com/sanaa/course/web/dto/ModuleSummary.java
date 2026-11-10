package com.sanaa.course.web.dto;

import java.util.List;

public record ModuleSummary(
        Long id,
        String title,
        int position,
        List<LessonSummary> lessons) {
}
