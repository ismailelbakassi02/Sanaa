package com.sanaa.course.web.dto.studio;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * The complete new order of a course's modules or a module's lessons. Positions are assigned by
 * the server from this list; a list that does not contain exactly the current children is rejected
 * so a concurrent creation cannot be silently dropped.
 */
public record ReorderRequest(@NotEmpty List<@NotNull Long> ids) {
}
