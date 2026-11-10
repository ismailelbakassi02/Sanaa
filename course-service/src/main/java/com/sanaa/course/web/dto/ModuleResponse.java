package com.sanaa.course.web.dto;

import com.sanaa.course.domain.Module;

public record ModuleResponse(
        Long id,
        Long courseId,
        String title,
        int position) {

    public static ModuleResponse from(Module module) {
        return new ModuleResponse(
                module.getId(),
                module.getCourseId(),
                module.getTitle(),
                module.getPosition());
    }
}
