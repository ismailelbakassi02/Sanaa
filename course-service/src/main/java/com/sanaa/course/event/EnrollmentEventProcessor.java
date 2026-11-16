package com.sanaa.course.event;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.PaymentEventPayload;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.service.CourseStatisticsService;

/**
 * Applies one {@code PAYMENT_APPROVED} event in its own transaction.
 *
 * <p>WHY a separate bean: the listener must set the tenant in {@code TenantContext} before the
 * transaction (and therefore the Hibernate session) opens, because Hibernate reads the tenant at
 * session start. Calling a {@code @Transactional} method on another bean means the proxy starts the
 * transaction only after the listener has set the context.
 *
 * <p>Idempotency has two layers: the {@code processed_events} primary key catches redelivery of the
 * same event, and the unique {@code (tenant_id, user_id, course_id)} enrollment key catches two
 * different events for the same purchase.
 */
@Service
public class EnrollmentEventProcessor {

    private final ProcessedEventRepository processedEventRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseStatisticsService courseStatisticsService;

    public EnrollmentEventProcessor(ProcessedEventRepository processedEventRepository,
                                    CourseRepository courseRepository,
                                    EnrollmentRepository enrollmentRepository,
                                    CourseStatisticsService courseStatisticsService) {
        this.processedEventRepository = processedEventRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseStatisticsService = courseStatisticsService;
    }

    /**
     * WHY READ_COMMITTED: the student counter is recounted under the course row lock, so a consumer
     * that waited for another payment event on the same course must see the enrollment that
     * committed while it waited.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventProcessingOutcome process(EventEnvelope<PaymentEventPayload> envelope) {
        if (processedEventRepository.markProcessed(envelope.eventId()) == 0) {
            return EventProcessingOutcome.DUPLICATE;
        }

        PaymentEventPayload payload = envelope.payload();
        // The student already paid, so an archived course still grants access. The lookup is
        // tenant-scoped by @TenantId, so a course id from another tenant is simply unknown here.
        if (!courseRepository.existsById(payload.courseId())) {
            // Retrying will not make the course appear; send the record to the DLT instead.
            throw new NonRetryableEventException(
                    "course " + payload.courseId() + " is unknown in tenant " + envelope.tenantId());
        }

        Optional<Enrollment> existing = enrollmentRepository
                .findByUserIdAndCourseId(payload.userId(), payload.courseId());
        if (existing.isPresent()) {
            // A second approval for the same purchase is a success: the student already has access.
            return EventProcessingOutcome.DUPLICATE;
        }

        // Lock before the insert: its foreign key check then reuses the exclusive lock instead of
        // taking a shared one and deadlocking against the recount lock.
        Course course = courseStatisticsService.lockCourse(payload.courseId());
        enrollmentRepository.saveAndFlush(new Enrollment(
                payload.userId(),
                payload.userName(),
                payload.userEmail(),
                payload.courseId(),
                payload.paymentId(),
                EnrollmentSource.PAYMENT,
                EnrollmentStatus.ACTIVE,
                Instant.now()));
        courseStatisticsService.recompute(course);
        return EventProcessingOutcome.PROCESSED;
    }
}
