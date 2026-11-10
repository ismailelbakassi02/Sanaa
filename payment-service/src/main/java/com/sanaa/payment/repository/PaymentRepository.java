package com.sanaa.payment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentStatus;

/**
 * All finders are tenant-filtered by Hibernate through {@code @TenantId}; no method accepts a
 * tenant id. Writes use {@code save} and rely on the generated active-key unique index.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
