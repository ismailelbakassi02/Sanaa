package com.sanaa.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sanaa.auth.domain.AdminAuditLog;

/**
 * The audit table is append-only: this repository exposes no update or delete path in the service,
 * and the database triggers reject both regardless of what the application does.
 */
public interface AdminAuditLogRepository
        extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {
}
