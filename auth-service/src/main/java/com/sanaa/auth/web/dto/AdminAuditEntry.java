package com.sanaa.auth.web.dto;

import java.time.Instant;

public record AdminAuditEntry(
        long id,
        String actorEmail,
        String action,
        String targetType,
        String targetId,
        String tenantId,
        String reason,
        Instant createdAt) {
}
