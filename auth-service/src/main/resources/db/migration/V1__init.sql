-- auth_db schema for the auth-service.
-- All tables use utf8mb4, timestamps are stored in UTC and ids are BIGINT except tenants (UUID).

CREATE TABLE tenants (
    id CHAR(36) NOT NULL,
    slug VARCHAR(63) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    primary_color VARCHAR(7) NOT NULL DEFAULT '#165DFF',
    logo_url VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenants_slug (slug),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_tenant_email (tenant_id, email),
    KEY idx_users_tenant_status (tenant_id, status),
    CONSTRAINT chk_users_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_users_role
        CHECK (role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'INSTRUCTOR', 'STUDENT')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_tenant_user (tenant_id, user_id),
    CONSTRAINT chk_refresh_tokens_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
