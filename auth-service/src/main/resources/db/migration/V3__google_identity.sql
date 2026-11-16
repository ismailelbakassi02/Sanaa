-- Google OpenID Connect identities are global, like users. A user may keep a password and a
-- Google identity at the same time; a Google-created account starts without a password and may set
-- one later. Provider access and ID tokens are never persisted.

ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(100) NULL;

CREATE TABLE external_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_identities_provider_subject (provider, provider_subject),
    UNIQUE KEY uk_external_identities_user_provider (user_id, provider),
    KEY idx_external_identities_email (provider_email),
    CONSTRAINT chk_external_identities_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT fk_external_identities_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- When Google verifies an email that already belongs to a password account, SANAA requires the
-- existing password before linking. The browser receives only the raw opaque ticket in an HttpOnly
-- cookie; this table stores its SHA-256 hash and expires it after ten minutes.
CREATE TABLE oauth_link_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_link_attempts_token_hash (token_hash),
    KEY idx_oauth_link_attempts_expiry (expires_at),
    KEY idx_oauth_link_attempts_user (user_id, provider),
    CONSTRAINT chk_oauth_link_attempts_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT fk_oauth_link_attempts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
