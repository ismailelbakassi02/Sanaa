package com.sanaa.course.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByInstructorIdOrStatus(Long instructorId, CourseStatus status, Pageable pageable);
}
