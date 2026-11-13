package com.sanaa.course.event;

import java.util.Locale;

/**
 * What the consumer did with one record. The name becomes the {@code result} tag of the
 * {@code sanaa_events_processed_total} counter.
 */
public enum EventProcessingOutcome {

    PROCESSED,
    DUPLICATE,
    IGNORED,
    DEAD_LETTERED;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
