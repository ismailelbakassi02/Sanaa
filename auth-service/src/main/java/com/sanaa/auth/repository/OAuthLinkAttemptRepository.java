package com.sanaa.auth.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.auth.domain.OAuthLinkAttempt;

/** Short-lived account-link tickets, looked up only by their SHA-256 hash. */
public interface OAuthLinkAttemptRepository extends JpaRepository<OAuthLinkAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from OAuthLinkAttempt attempt where attempt.tokenHash = :tokenHash")
    Optional<OAuthLinkAttempt> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long deleteByExpiresAtBefore(Instant now);
}
