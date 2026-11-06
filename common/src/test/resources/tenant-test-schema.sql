-- Test-only schema. This is the documented exception to the Flyway rule: common is a library and
-- has no production database, so the integration test creates its own isolated schema.
CREATE TABLE tenant_test_entities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_tenant_test_entities_tenant (tenant_id),
    CONSTRAINT chk_tenant_not_nil CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
