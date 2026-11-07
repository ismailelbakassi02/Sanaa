package com.sanaa.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);
}
