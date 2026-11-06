package com.sanaa.common.tenant;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Request-scoped tenant identifier carried in a {@link ThreadLocal}.
 *
 * <p>WHY: Hibernate reads the tenant when it opens a session. The context must therefore be
 * populated before the transaction starts and must never leak between requests or pooled threads.
 * The nil tenant is the fail-closed fallback used by {@link TenantIdentifierResolver}.
 */
public final class TenantContext {

    /** Sentinel used when no tenant is present. Reads match nothing and inserts fail the DB CHECK. */
    public static final String NIL_TENANT = "00000000-0000-0000-0000-000000000000";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            clear();
            return;
        }
        CURRENT.set(tenantId);
    }

    /**
     * Returns the current tenant or {@code null} when none is set.
     * Callers that need a non-null value should use {@link #getOrNil()}.
     */
    public static String get() {
        return CURRENT.get();
    }

    public static String getOrNil() {
        String tenantId = CURRENT.get();
        return tenantId == null ? NIL_TENANT : tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs the supplier under the given tenant and restores the previous value in a finally block.
     */
    public static <T> T callAs(String tenantId, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        String previous = CURRENT.get();
        set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Runs the runnable under the given tenant and restores the previous value in a finally block.
     */
    public static void runAs(String tenantId, Runnable action) {
        Objects.requireNonNull(action, "action");
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(String previous) {
        if (previous == null) {
            clear();
        } else {
            CURRENT.set(previous);
        }
    }
}
