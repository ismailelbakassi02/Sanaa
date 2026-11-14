package com.sanaa.auth.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.auth.domain.Membership;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.MembershipStatus;

/**
 * Tenant-scoped membership access. {@code @TenantId} adds the publisher filter to every query, so
 * the caller never passes a tenant id and cannot widen a query to another studio.
 */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    Page<Membership> findAllByOrderByIdAsc(Pageable pageable);

    long countByRoleAndStatus(MembershipRole role, MembershipStatus status);

    List<Membership> findAllByStatus(MembershipStatus status);

    /**
     * Locks every active admin row of the current tenant for the rest of the transaction.
     *
     * <p>WHY: the "last active admin" rule must hold under two concurrent requests. Both
     * transactions take the same row locks in the same order, so the second one sees the first
     * one's committed change before it counts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.role = :role and m.status = :status order by m.id")
    List<Membership> lockByRoleAndStatus(@Param("role") MembershipRole role,
                                         @Param("status") MembershipStatus status);
}
