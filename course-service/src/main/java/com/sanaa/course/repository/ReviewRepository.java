package com.sanaa.course.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.course.domain.Review;
import com.sanaa.course.domain.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByCourseIdAndUserId(Long courseId, Long userId);

    Page<Review> findByCourseIdAndStatus(Long courseId, ReviewStatus status, Pageable pageable);

    Page<Review> findByCourseId(Long courseId, Pageable pageable);

    /**
     * Average and count of visible reviews.
     *
     * <p>WHY a projection instead of {@code Object[]}: a multi-column query declared as
     * {@code Object[]} is read back as the array of rows, so a two-column single row only matched by
     * accident and the counters silently stayed at zero. The aliased projection keeps the shape
     * typed and fails to compile if the query changes.
     *
     * <p>The aggregate is the single definition of a course rating: HIDDEN reviews never count.
     */
    @Query("""
            select coalesce(avg(r.rating), 0) as average, count(r) as total
            from Review r
            where r.courseId = :courseId and r.status = com.sanaa.course.domain.ReviewStatus.VISIBLE
            """)
    RatingSummary ratingSummary(@Param("courseId") Long courseId);

    /** One row of {@link #ratingSummary(Long)}: {@code average} rounded by the caller. */
    interface RatingSummary {

        Double getAverage();

        Long getTotal();
    }
}
