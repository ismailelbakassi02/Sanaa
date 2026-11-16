package com.sanaa.course.web.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModuleRequest(@NotBlank @Size(max = 120) String title) {
}
