package com.sanaa.auth.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Tenant> findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(
            String name, String slug, Pageable pageable);
}
