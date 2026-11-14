package com.sanaa.auth.crosstenant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.MembershipStatus;

/**
 * Cross-tenant membership reads used by login/refresh and by the public pages. There is deliberately
 * no write method and no native SQL.
 */
public interface MembershipViewRepository extends JpaRepository<MembershipView, Long> {

    Optional<MembershipView> findByUserId(Long userId);

    List<MembershipView> findAllByUserIdIn(Collection<Long> userIds);

    List<MembershipView> findAllByTenantIdAndStatusOrderByIdAsc(String tenantId,
                                                               MembershipStatus status);

    long countByTenantIdAndStatus(String tenantId, MembershipStatus status);
}
