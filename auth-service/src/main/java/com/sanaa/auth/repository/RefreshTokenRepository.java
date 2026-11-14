package com.sanaa.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.auth.domain.RefreshToken;

/**
 * Refresh-token lookups are deliberately not tenant-scoped: the cookie is the only input, so the
 * hash must identify the row before the tenant can be read from it. The conditional updates are the
 * rotation and reuse-detection primitives.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.tenantId = :tenantId and t.revokedAt is null")
    int revokeAllForTenant(@Param("tenantId") String tenantId, @Param("now") Instant now);
}
