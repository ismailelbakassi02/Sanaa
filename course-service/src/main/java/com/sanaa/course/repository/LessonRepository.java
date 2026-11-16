package com.sanaa.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.MediaStatus;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByCourseIdOrderByPositionAsc(Long courseId);

    int countByCourseId(Long courseId);

    long countByCourseIdAndMediaStatus(Long courseId, MediaStatus mediaStatus);

    void deleteByCourseId(Long courseId);

    void deleteByModuleId(Long moduleId);

    @Query("select coalesce(max(l.position), 0) from Lesson l where l.moduleId = :moduleId")
    int findMaxPosition(@Param("moduleId") Long moduleId);
}
