package com.sanaa.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.CourseTag;

public interface CourseTagRepository extends JpaRepository<CourseTag, Long> {

    List<CourseTag> findByCourseIdOrderByTagAsc(Long courseId);

    void deleteByCourseId(Long courseId);

    long countByCourseId(Long courseId);
}
