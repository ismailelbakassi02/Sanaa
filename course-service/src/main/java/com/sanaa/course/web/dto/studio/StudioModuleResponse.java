package com.sanaa.course.web.dto.studio;

import java.util.List;

import com.sanaa.course.domain.Module;

public record StudioModuleResponse(
        long id,
        String title,
        int position,
        List<StudioLessonResponse> lessons) {
}
