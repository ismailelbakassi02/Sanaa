package com.sanaa.common.event;

/**
 * Event types shared by every Kafka topic.
 *
 * <p>Payment types travel on {@code sanaa.payments.v1}, publisher types on
 * {@code sanaa.publishers.v1} and complaint types on {@code sanaa.support.v1}. The type is what
 * tells a consumer which payload record to deserialize.
 */
public enum EventType {
    PAYMENT_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    PUBLISHER_UPSERTED,
    INSTRUCTOR_UPDATED,
    COMPLAINT_OPENED,
    COMPLAINT_MESSAGE_ADDED,
    COMPLAINT_RESOLVED
}
