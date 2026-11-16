package com.sanaa.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.course.domain.PublisherSnapshot;

/**
 * Used only by the publisher-event consumer and the studio placeholder rule. Public and learner
 * reads go through the {@code crosstenant} repositories, never through this one.
 */
public interface PublisherSnapshotRepository extends JpaRepository<PublisherSnapshot, String> {
}
