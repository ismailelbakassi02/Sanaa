-- payment_db schema for the payment-service.
-- Tenant-owned tables carry tenant_id, the nil-UUID CHECK and a tenant-leading index.
-- Money is DECIMAL(10,2); timestamps are stored in UTC.

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    course_id BIGINT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    method VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    receipt_path VARCHAR(255) NOT NULL,
    receipt_content_type VARCHAR(64) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    rejection_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    -- One open payment per user and course. The key is NULL for REJECTED rows so a
    -- rejected payment frees the slot; the unique index ignores NULL values.
    active_key VARCHAR(80) GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PENDING_REVIEW', 'APPROVED')
                THEN CONCAT(tenant_id, ':', user_id, ':', course_id)
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_active_key (active_key),
    KEY idx_payments_tenant_user (tenant_id, user_id, created_at),
    KEY idx_payments_tenant_status (tenant_id, status, created_at),
    CONSTRAINT chk_payments_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_payments_method CHECK (method IN ('BANK_WIRE', 'CASHPLUS')),
    CONSTRAINT chk_payments_status
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_currency CHECK (currency = 'MAD')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Not tenant-owned: the future outbox poller must read rows for every tenant, and
-- native SQL there is the documented exception to the @TenantId rule.
CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_events_event_id (event_id),
    KEY idx_outbox_events_published (published_at, id),
    KEY idx_outbox_events_aggregate (aggregate_id),
    CONSTRAINT chk_outbox_events_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_outbox_events_type
        CHECK (event_type IN ('PAYMENT_SUBMITTED', 'PAYMENT_APPROVED', 'PAYMENT_REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
