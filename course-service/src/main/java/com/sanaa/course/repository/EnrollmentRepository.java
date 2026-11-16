package com.sanaa.course.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status);

    /**
     * Tenant-scoped through {@code @TenantId}; used by the consumer and by free enrollment to detect
     * an existing access whatever its status.
     */
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    Page<Enrollment> findByCourseIdAndStatus(Long courseId, EnrollmentStatus status,
                                             Pageable pageable);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    long countByCourseIdInAndStatus(Collection<Long> courseIds, EnrollmentStatus status);

    long countByStatusAndEnrolledAtGreaterThanEqual(EnrollmentStatus status, Instant since);

    long countByCourseIdInAndStatusAndEnrolledAtGreaterThanEqual(Collection<Long> courseIds,
                                                                 EnrollmentStatus status,
                                                                 Instant since);
}
