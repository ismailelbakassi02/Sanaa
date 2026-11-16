package com.sanaa.course.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.ReviewRepository;

/**
 * The one writer of the denormalised course counters ({@code rating_avg}, {@code rating_count},
 * {@code student_count}).
 *
 * <p>WHY a shared method: the counters are derived data, so every path that can change a review or
 * an enrollment must write exactly the same definition: free enrollment, the payment consumer, the
 * future admin grant, and the admin moderation task that hides a review and must recount after it.
 * Nothing is incremental on purpose: a full recount cannot drift after a redelivery, a manual fix or
 * a moderation hide.
 *
 * <p>WHY the lock and READ_COMMITTED callers: ten learners reviewing one course at the same time
 * must end with the count of ten, not with whichever transaction committed last. The write paths
 * lock the course row before they insert their child row, and keep it until the aggregate has been
 * written, so the next writer sees the committed count. They run at READ_COMMITTED so the aggregate
 * also sees the row the previous writer committed while this one waited.
 */
@Service
public class CourseStatisticsService {

    private final CourseRepository courseRepository;
    private final ReviewRepository reviewRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseStatisticsService(CourseRepository courseRepository,
                                   ReviewRepository reviewRepository,
                                   EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.reviewRepository = reviewRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Locks the course row and returns it, inside the caller's transaction.
     *
     * <p>WHY the write paths call this before inserting the enrollment or the review: the insert
     * takes a shared lock on the parent row for its foreign key check. If the same transaction then
     * asks for the exclusive lock, two concurrent inserts each hold the shared lock and wait for
     * the other's exclusive one, and MySQL reports a deadlock. Taking the exclusive lock first makes
     * the foreign key check reuse the lock already held.
     */
    @Transactional
    public Course lockCourse(long courseId) {
        return courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new IllegalStateException(
                        "Course " + courseId + " vanished during a counter recount."));
    }

    /**
     * Recounts and stores the rating and the student count of one course, in the caller's tenant
     * and transaction.
     */
    @Transactional
    public void recompute(long courseId) {
        recompute(lockCourse(courseId));
    }

    /**
     * The recount itself, for a caller that already holds the course lock (the two write paths).
     */
    @Transactional
    public void recompute(Course course) {
        long courseId = course.getId();
        ReviewRepository.RatingSummary summary = reviewRepository.ratingSummary(courseId);
        BigDecimal average = average(summary);
        int reviewCount = summary == null || summary.getTotal() == null
                ? 0 : summary.getTotal().intValue();
        long students = enrollmentRepository
                .countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE);

        course.updateRating(average, reviewCount);
        course.updateStudentCount(students);
        // No explicit save: the entity is managed and the enclosing transaction flushes it.
    }

    /** Rounded to the scale of {@code courses.rating_avg}; a null average means no visible review. */
    private static BigDecimal average(ReviewRepository.RatingSummary summary) {
        if (summary == null || summary.getAverage() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(summary.getAverage()).setScale(2, RoundingMode.HALF_UP);
    }
}
