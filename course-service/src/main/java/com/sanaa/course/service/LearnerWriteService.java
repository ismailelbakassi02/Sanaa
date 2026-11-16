package com.sanaa.course.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.Review;
import com.sanaa.course.error.ConflictException;
import com.sanaa.course.error.ForbiddenException;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.ReviewRepository;
import com.sanaa.course.web.dto.learner.MyReviewResponse;

/**
 * Cross-tenant writes of a learner, executed inside {@code TenantContext.callAs(course tenant)}.
 *
 * <p>WHY a separate bean: the caller must set the tenant <em>before</em> the transaction opens. A
 * private method annotated {@code @Transactional} would not start a transaction at all (self
 * invocation bypasses the proxy), and an outer method would open the transaction before the tenant
 * is known. The facade sets the context, then calls this bean through the proxy.
 *
 * <p>WHY READ_COMMITTED: the denormalised counters are recounted under a course row lock, and the
 * writer that waited for the lock must see the review or enrollment the previous writer committed.
 * The default REPEATABLE READ snapshot would still show the state from before the wait.
 */
@Service
public class LearnerWriteService {

    private final EnrollmentRepository enrollmentRepository;
    private final ReviewRepository reviewRepository;
    private final CourseStatisticsService courseStatisticsService;

    public LearnerWriteService(EnrollmentRepository enrollmentRepository,
                               ReviewRepository reviewRepository,
                               CourseStatisticsService courseStatisticsService) {
        this.enrollmentRepository = enrollmentRepository;
        this.reviewRepository = reviewRepository;
        this.courseStatisticsService = courseStatisticsService;
    }

    /** Idempotent: an existing ACTIVE enrollment is returned instead of a second row. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentRecord enroll(long userId, String userName, String userEmail, long courseId,
                                   Instant now) {
        // Lock before the insert: the foreign key check of the enrollment reuses the exclusive lock
        // instead of taking a shared one and deadlocking against the recount lock.
        Course course = courseStatisticsService.lockCourse(courseId);
        Enrollment existing = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == EnrollmentStatus.ACTIVE) {
                return EnrollmentRecord.from(existing);
            }
            throw new ConflictException("An access already exists for this course.");
        }
        Enrollment enrollment = enrollmentRepository.saveAndFlush(new Enrollment(
                userId,
                userName,
                userEmail,
                courseId,
                null,
                EnrollmentSource.FREE,
                EnrollmentStatus.ACTIVE,
                now));
        courseStatisticsService.recompute(course);
        return EnrollmentRecord.from(enrollment);
    }

    /**
     * One review per learner and course. The aggregate is recomputed in the same transaction, over
     * VISIBLE reviews only, and written to the course row so catalogue pages need no join.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MyReviewResponse upsertReview(long userId, String userName, long courseId, int rating,
                                         String comment) {
        // Same reason as enroll: lock the parent row before the review insert so its foreign key
        // check cannot deadlock with the recount lock.
        Course course = courseStatisticsService.lockCourse(courseId);
        boolean enrolled = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .isPresent();
        if (!enrolled) {
            throw new ForbiddenException("An active enrollment is required to review a course.");
        }

        Review review = reviewRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseGet(() -> new Review(courseId, userId,
                        userName == null || userName.isBlank() ? "Learner " + userId : userName,
                        rating, comment));
        review.edit(rating, comment);
        reviewRepository.saveAndFlush(review);
        courseStatisticsService.recompute(course);
        return MyReviewResponse.from(review);
    }

    /** The committed identity of a free enrollment, safe to pass back to the facade. */
    public record EnrollmentRecord(long id, Instant enrolledAt, String source) {

        static EnrollmentRecord from(Enrollment enrollment) {
            return new EnrollmentRecord(enrollment.getId(), enrollment.getEnrolledAt(),
                    enrollment.getSource().name());
        }
    }
}
