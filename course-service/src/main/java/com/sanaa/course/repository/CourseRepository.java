package com.sanaa.course.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * The row locked for a counter recount.
     *
     * <p>WHY a lock: two concurrent review or enrollment writes on the same course must serialise
     * their recount, otherwise both read the same numbers and the later commit overwrites the
     * earlier total with a stale one. The lock is taken before the aggregate query, so a writer
     * that waited for it also sees the row the previous writer committed (its transaction runs at
     * READ_COMMITTED).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    /**
     * Studio listing. Hibernate appends the {@code @TenantId} predicate, so a tenant admin can
     * never see another publisher's courses even when passing an instructor id.
     *
     * <p>{@code instructorId} is the ownership scope: null for a TENANT_ADMIN, the JWT subject for
     * an INSTRUCTOR. {@code q} is a simple contains search; the public catalogue uses FULLTEXT.
     */
    @Query("""
            select c from Course c
            where (:status is null or c.status = :status)
              and (:instructorId is null or c.instructorId = :instructorId)
              and (:q is null
                   or lower(c.title) like lower(concat('%', :q, '%'))
                   or lower(c.summary) like lower(concat('%', :q, '%')))
            """)
    Page<Course> searchForStudio(@Param("instructorId") Long instructorId,
                                 @Param("status") CourseStatus status,
                                 @Param("q") String q,
                                 Pageable pageable);

    @Query("select coalesce(avg(c.ratingAvg), 0) from Course c where (:instructorId is null or c.instructorId = :instructorId)")
    Double averageRating(@Param("instructorId") Long instructorId);

    @Query("""
            select c.status, count(c) from Course c
            where (:instructorId is null or c.instructorId = :instructorId)
            group by c.status
            """)
    List<Object[]> countByStatus(@Param("instructorId") Long instructorId);

    @Query("select c.id from Course c where (:instructorId is null or c.instructorId = :instructorId)")
    List<Long> findIdsForStatistics(@Param("instructorId") Long instructorId);

    long countByInstructorId(Long instructorId);
}
