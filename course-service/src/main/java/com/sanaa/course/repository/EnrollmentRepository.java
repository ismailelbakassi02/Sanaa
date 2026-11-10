package com.sanaa.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status);
}
