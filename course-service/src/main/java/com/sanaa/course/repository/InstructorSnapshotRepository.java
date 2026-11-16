package com.sanaa.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.InstructorSnapshot;

/**
 * Used only by the publisher-event consumer and the studio placeholder rule.
 */
public interface InstructorSnapshotRepository extends JpaRepository<InstructorSnapshot, Long> {
}
