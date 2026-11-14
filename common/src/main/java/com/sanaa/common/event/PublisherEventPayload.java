package com.sanaa.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Publisher data carried inside an {@link EventEnvelope} on {@code sanaa.publishers.v1}.
 *
 * <p>The event is an upsert: consumers replace their snapshot of the publisher. {@code type},
 * {@code status} and {@code primaryColor} are strings so the wire contract does not depend on an
 * enum that a consumer may not know yet. Null fields are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublisherEventPayload(
        String publisherId,
        String slug,
        String name,
        String type,
        String status,
        String contactEmail,
        String primaryColor
) {
}
