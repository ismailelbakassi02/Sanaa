package com.sanaa.course.event;

import java.time.Instant;

import com.sanaa.common.event.EventType;
import com.sanaa.common.event.InstructorEventPayload;
import com.sanaa.common.event.PublisherEventPayload;

/**
 * A validated record from {@code sanaa.publishers.v1}. The topic carries two payload shapes, so the
 * validator resolves the type first and puts exactly one payload in this value object.
 */
public record PublisherEvent(
        String eventId,
        EventType eventType,
        Instant occurredAt,
        String tenantId,
        PublisherEventPayload publisher,
        InstructorEventPayload instructor) {
}
