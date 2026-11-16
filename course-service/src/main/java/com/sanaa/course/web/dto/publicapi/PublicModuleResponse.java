package com.sanaa.course.web.dto.publicapi;

import java.util.List;

public record PublicModuleResponse(
        long id,
        String title,
        int position,
        List<PublicLessonResponse> lessons) {
}
