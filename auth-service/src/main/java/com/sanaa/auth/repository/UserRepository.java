package com.sanaa.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.User;

/**
 * All queries are automatically filtered by {@code @TenantId}. Callers therefore never pass a
 * tenant id and cannot accidentally widen a query to another tenant.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
