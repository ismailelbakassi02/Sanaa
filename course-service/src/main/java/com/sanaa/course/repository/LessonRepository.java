package com.sanaa.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.course.domain.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByCourseIdOrderByPositionAsc(Long courseId);

    int countByCourseId(Long courseId);

    @Query("select coalesce(max(l.position), 0) from Lesson l where l.moduleId = :moduleId")
    int findMaxPosition(@Param("moduleId") Long moduleId);
}
