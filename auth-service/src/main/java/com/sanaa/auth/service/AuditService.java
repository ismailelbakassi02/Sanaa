package com.sanaa.auth.service;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.sanaa.auth.domain.AdminAuditLog;
import com.sanaa.auth.repository.AdminAuditLogRepository;
import com.sanaa.common.security.JwtClaims;

/**
 * Appends admin audit rows.
 *
 * <p>Every admin call is recorded, including reads of personal data. The row is inserted in the
 * caller's transaction, so a failed admin action leaves no audit row and a successful one cannot
 * lose it. UPDATE and DELETE are blocked by database triggers, not only by this class.
 */
@Service
public class AuditService {

    public static final String USER_LISTED = "USER_LISTED";
    public static final String USER_READ = "USER_READ";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String PUBLISHER_LISTED = "PUBLISHER_LISTED";
    public static final String PUBLISHER_STATUS_CHANGED = "PUBLISHER_STATUS_CHANGED";
    public static final String AUDIT_LISTED = "AUDIT_LISTED";

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AuditService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    public void record(Jwt jwt, String action, String targetType, String targetId,
                       String tenantId, String reason) {
        String email = jwt.getClaimAsString(JwtClaims.EMAIL);
        adminAuditLogRepository.save(new AdminAuditLog(actorId(jwt),
                email == null ? "unknown" : email, action, targetType, targetId, tenantId, reason,
                Instant.now()));
    }

    private static Long actorId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
