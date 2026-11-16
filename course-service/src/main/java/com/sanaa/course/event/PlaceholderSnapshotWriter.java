package com.sanaa.course.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.sanaa.common.tenant.TenantContext;

/**
 * Creates the placeholder snapshots a studio needs before the first publisher event arrives.
 *
 * <p>WHY native {@code INSERT IGNORE}: the snapshot tables are global (no {@code @TenantId}) and an
 * existing row must never be overwritten here. A plain JPA insert would raise a duplicate-key error
 * under two concurrent first-course creations and roll the whole course transaction back.
 * {@code INSERT IGNORE} reports "zero rows inserted" instead, and the event consumer later replaces
 * the placeholder because its event time is after {@link Instant#EPOCH}.
 */
@Component
public class PlaceholderSnapshotWriter {

    private static final LocalDateTime EPOCH =
            LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    @PersistenceContext
    private EntityManager entityManager;

    public void ensurePublisher(String tenantId, String slug, String type) {
        if (tenantId == null || TenantContext.NIL_TENANT.equals(tenantId)) {
            return;
        }
        String safeSlug = textOr(slug, tenantId, 63);
        String safeType = "ORGANIZATION".equals(type) ? "ORGANIZATION" : "INDIVIDUAL";
        entityManager.createNativeQuery("""
                        INSERT IGNORE INTO publisher_snapshots
                            (tenant_id, slug, name, type, status, source_occurred_at)
                        VALUES (:tenantId, :slug, :slug, :type, 'ACTIVE', :sourceOccurredAt)
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("slug", safeSlug)
                .setParameter("type", safeType)
                .setParameter("sourceOccurredAt", EPOCH)
                .executeUpdate();
    }

    public void ensureInstructor(long userId, String tenantId, String name) {
        if (tenantId == null || TenantContext.NIL_TENANT.equals(tenantId)) {
            return;
        }
        String safeName = textOr(name, "Instructor " + userId, 120);
        entityManager.createNativeQuery("""
                        INSERT IGNORE INTO instructor_snapshots
                            (user_id, tenant_id, name, source_occurred_at)
                        VALUES (:userId, :tenantId, :name, :sourceOccurredAt)
                        """)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .setParameter("name", safeName)
                .setParameter("sourceOccurredAt", EPOCH)
                .executeUpdate();
    }

    private static String textOr(String value, String fallback, int maxLength) {
        String chosen = value == null || value.isBlank() ? fallback : value.trim();
        return chosen.length() <= maxLength ? chosen : chosen.substring(0, maxLength);
    }
}
