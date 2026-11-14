package com.sanaa.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Instructor snapshot carried inside an {@link EventEnvelope} on {@code sanaa.publishers.v1}.
 *
 * <p>Consumers keep a course-level instructor name snapshot, so a renamed instructor only needs
 * this small payload. {@code tenantId} is duplicated from the envelope on purpose: a course
 * snapshot is scoped to one publisher, and the consumer uses the payload directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstructorEventPayload(
        long userId,
        String tenantId,
        String name
) {
}
