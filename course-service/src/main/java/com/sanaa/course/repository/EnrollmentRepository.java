package com.sanaa.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status);

    /**
     * Tenant-scoped through {@code @TenantId}; used by the consumer to detect an existing access
     * whatever its status.
     */
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);
}
