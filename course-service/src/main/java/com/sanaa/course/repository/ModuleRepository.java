package com.sanaa.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.course.domain.Module;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findByCourseIdOrderByPositionAsc(Long courseId);

    int countByCourseId(Long courseId);

    /**
     * Server-assigned positions: the next module always follows the current maximum. The unique
     * index {@code (course_id, position)} turns a concurrent collision into a database conflict,
     * which the API maps to 409 instead of a 500.
     */
    @Query("select coalesce(max(m.position), 0) from Module m where m.courseId = :courseId")
    int findMaxPosition(@Param("courseId") Long courseId);
}
