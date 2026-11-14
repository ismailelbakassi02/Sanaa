-- auth_db V2: publisher model.
--
-- Identity becomes global: a user is no longer owned by a tenant. A publisher is a tenant, and a
-- membership links a user to one publisher. Learners and SUPER_ADMIN have no membership.
--
-- This migration transforms the V1 schema in place; V1 is never edited. It also migrates the data:
--   * every old tenant user becomes a global user;
--   * old TENANT_ADMIN and INSTRUCTOR users get a membership in their old tenant;
--   * old STUDENT users become plain learners (no membership);
--   * the old "platform" tenant row is removed and its SUPER_ADMIN becomes a global user with
--     platform_role SUPER_ADMIN and no membership;
--   * atlas is an ORGANIZATION, acme an INDIVIDUAL;
--   * emails shared by two old tenants are made unique: the lowest user id keeps the address and
--     later rows become local+<tenantSlug>@domain (example: alice@example.com ->
--     alice+atlas@example.com). Every rename is written to admin_audit_log with action
--     MIGRATION_EMAIL_RENAMED; list them with:
--       SELECT * FROM admin_audit_log WHERE action = 'MIGRATION_EMAIL_RENAMED';

-- ---------------------------------------------------------------------------- images
-- Created first because tenants.logo_image_id and profiles.avatar_image_id reference it.
CREATE TABLE images (
    id CHAR(36) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    path VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_images_content_type
        CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT chk_images_size CHECK (size_bytes > 0 AND size_bytes <= 1048576)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------- tenants
ALTER TABLE tenants
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'ORGANIZATION' AFTER name,
    ADD COLUMN headline VARCHAR(160) NULL AFTER type,
    ADD COLUMN description VARCHAR(2000) NULL AFTER headline,
    ADD COLUMN website_url VARCHAR(200) NULL AFTER description,
    ADD COLUMN instagram_url VARCHAR(200) NULL AFTER website_url,
    ADD COLUMN linkedin_url VARCHAR(200) NULL AFTER instagram_url,
    ADD COLUMN youtube_url VARCHAR(200) NULL AFTER linkedin_url,
    ADD COLUMN logo_image_id CHAR(36) NULL AFTER youtube_url,
    ADD COLUMN contact_email VARCHAR(255) NULL AFTER logo_image_id;

UPDATE tenants SET type = 'INDIVIDUAL' WHERE slug = 'acme';

ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_type CHECK (type IN ('INDIVIDUAL', 'ORGANIZATION')),
    ADD CONSTRAINT fk_tenants_logo_image FOREIGN KEY (logo_image_id) REFERENCES images (id),
    ADD KEY idx_tenants_status_type (status, type);

-- V1 stored a free-form logo URL; v2 stores an uploaded image id instead.
ALTER TABLE tenants DROP COLUMN logo_url;

-- ---------------------------------------------------------------------------- memberships
CREATE TABLE memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- v1.0.0 allows at most one publisher per user.
    UNIQUE KEY uk_memberships_user (user_id),
    KEY idx_memberships_tenant_status (tenant_id, status),
    CONSTRAINT chk_memberships_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_memberships_role CHECK (role IN ('TENANT_ADMIN', 'INSTRUCTOR')),
    CONSTRAINT chk_memberships_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Old admins and instructors keep their role, now through a membership. Old students become plain
-- learners. The platform tenant is excluded: its admin becomes a platform SUPER_ADMIN below.
INSERT INTO memberships (tenant_id, user_id, role, status, created_at, updated_at)
SELECT u.tenant_id, u.id, u.role, u.status, u.created_at, u.updated_at
FROM users u
JOIN tenants t ON t.id = u.tenant_id
WHERE t.slug <> 'platform'
  AND u.role IN ('TENANT_ADMIN', 'INSTRUCTOR');

-- ---------------------------------------------------------------------------- profiles
CREATE TABLE profiles (
    user_id BIGINT NOT NULL,
    headline VARCHAR(120) NULL,
    bio VARCHAR(1000) NULL,
    website_url VARCHAR(200) NULL,
    instagram_url VARCHAR(200) NULL,
    linkedin_url VARCHAR(200) NULL,
    youtube_url VARCHAR(200) NULL,
    avatar_image_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_profiles_avatar FOREIGN KEY (avatar_image_id) REFERENCES images (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------- outbox
-- Same design as payment-service: not tenant-scoped, read across tenants by the poller with
-- FOR UPDATE SKIP LOCKED. The aggregate id of a publisher event is the tenant UUID.
CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
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
        CHECK (event_type IN ('PUBLISHER_UPSERTED', 'INSTRUCTOR_UPDATED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------- audit
CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NULL,
    tenant_id CHAR(36) NULL,
    reason VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_actor (actor_email, created_at),
    KEY idx_audit_action (action, created_at),
    KEY idx_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- --------------------------------------------------------- email conflicts across old tenants
-- A staging table keeps the rename list available for the audit rows below. It is dropped at the
-- end of this migration and never becomes part of the schema.
CREATE TABLE migration_email_renames (
    user_id BIGINT NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    old_email VARCHAR(255) NOT NULL,
    new_email VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO migration_email_renames (user_id, tenant_id, old_email, new_email)
SELECT u.id,
       u.tenant_id,
       u.email,
       CONCAT(SUBSTRING_INDEX(u.email, '@', 1), '+', t.slug, '@',
              SUBSTRING_INDEX(u.email, '@', -1))
FROM users u
JOIN tenants t ON t.id = u.tenant_id
JOIN (SELECT email, MIN(id) AS keep_id
      FROM users
      GROUP BY email
      HAVING COUNT(*) > 1) duplicates
  ON duplicates.email = u.email
 AND duplicates.keep_id <> u.id;

INSERT INTO admin_audit_log
    (actor_user_id, actor_email, action, target_type, target_id, tenant_id, reason, created_at)
SELECT NULL,
       'system:migration-v2',
       'MIGRATION_EMAIL_RENAMED',
       'USER',
       CAST(r.user_id AS CHAR),
       r.tenant_id,
       CONCAT('Email ', r.old_email, ' renamed to ', r.new_email),
       UTC_TIMESTAMP(6)
FROM migration_email_renames r;

UPDATE users u
JOIN migration_email_renames r ON r.user_id = u.id
SET u.email = r.new_email;

DROP TABLE migration_email_renames;

-- ---------------------------------------------------------------------------- users
-- Users are global now. The old per-tenant uniqueness and the tenant discriminator are replaced by
-- a single unique email and a platform role; publisher roles live in memberships.
ALTER TABLE users DROP CHECK chk_users_tenant_not_nil;
ALTER TABLE users DROP CHECK chk_users_role;
ALTER TABLE users DROP INDEX uk_users_tenant_email;
ALTER TABLE users DROP INDEX idx_users_tenant_status;

ALTER TABLE users CHANGE COLUMN role legacy_role VARCHAR(32) NOT NULL;
ALTER TABLE users ADD COLUMN platform_role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER full_name;
UPDATE users SET platform_role = 'SUPER_ADMIN' WHERE legacy_role = 'SUPER_ADMIN';
ALTER TABLE users DROP COLUMN legacy_role;

ALTER TABLE users DROP COLUMN tenant_id;
ALTER TABLE users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE users ADD UNIQUE KEY uk_users_email (email);
ALTER TABLE users
    ADD CONSTRAINT chk_users_platform_role CHECK (platform_role IN ('USER', 'SUPER_ADMIN'));

-- The platform tenant no longer exists; its SUPER_ADMIN is now a global user with no membership.
DELETE FROM tenants WHERE slug = 'platform';

-- ---------------------------------------------------------------------------- refresh tokens
-- A learner has no tenant, so a refresh session can no longer require one.
ALTER TABLE refresh_tokens DROP CHECK chk_refresh_tokens_tenant_not_nil;
ALTER TABLE refresh_tokens MODIFY COLUMN tenant_id CHAR(36) NULL;
ALTER TABLE refresh_tokens ADD KEY idx_refresh_tokens_user (user_id);

-- ---------------------------------------------------------------------------- append-only audit
-- The audit trail must not be rewritable, not even by a future code path that forgets the rule.
CREATE TRIGGER trg_admin_audit_log_no_update
BEFORE UPDATE ON admin_audit_log
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'admin_audit_log is append-only';

CREATE TRIGGER trg_admin_audit_log_no_delete
BEFORE DELETE ON admin_audit_log
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'admin_audit_log is append-only';
