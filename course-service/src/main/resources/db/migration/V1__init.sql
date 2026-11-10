-- course_db schema for the course-service.
-- Tenant-owned tables carry tenant_id, the nil-UUID CHECK and a tenant-leading index.
-- Money is DECIMAL(10,2); timestamps are stored in UTC.

CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(4000) NULL,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    status VARCHAR(16) NOT NULL,
    instructor_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_courses_tenant_status (tenant_id, status),
    KEY idx_courses_tenant_instructor (tenant_id, instructor_id),
    CONSTRAINT chk_courses_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_courses_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_courses_currency CHECK (currency = 'MAD'),
    CONSTRAINT chk_courses_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE modules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_modules_course_position (course_id, position),
    KEY idx_modules_tenant_course (tenant_id, course_id),
    CONSTRAINT chk_modules_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_modules_position CHECK (position > 0),
    CONSTRAINT fk_modules_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    course_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    position INT NOT NULL,
    duration_seconds INT NOT NULL,
    media_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    free_preview BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lessons_module_position (module_id, position),
    KEY idx_lessons_tenant_course (tenant_id, course_id),
    KEY idx_lessons_tenant_module (tenant_id, module_id),
    CONSTRAINT chk_lessons_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_lessons_position CHECK (position > 0),
    CONSTRAINT chk_lessons_duration CHECK (duration_seconds >= 0),
    CONSTRAINT chk_lessons_media_status CHECK (media_status IN ('NONE', 'READY')),
    CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES modules (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    source_payment_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    enrolled_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_enrollments_tenant_user_course (tenant_id, user_id, course_id),
    KEY idx_enrollments_tenant_user_status (tenant_id, user_id, status),
    CONSTRAINT chk_enrollments_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Not tenant-owned: consumers record an event id once for idempotency across all tenants.
CREATE TABLE processed_events (
    event_id CHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
