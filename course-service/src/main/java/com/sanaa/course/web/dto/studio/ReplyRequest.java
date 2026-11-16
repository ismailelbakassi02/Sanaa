package com.sanaa.course.web.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyRequest(@NotBlank @Size(max = 1000) String reply) {
}
