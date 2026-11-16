-- V2: marketplace catalogue, publisher snapshots, reviews and daily views.
-- V1 is never edited. Every statement below must transform a populated V1 database.

-- Global reference data. Categories are shared by every publisher, so they are not tenant-owned.
CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(64) NOT NULL,
    name_fr VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO categories (slug, name_fr) VALUES
    ('montage-video', 'Montage et vidéo'),
    ('photographie', 'Photographie'),
    ('design-graphique', 'Design graphique'),
    ('musique-son', 'Musique et son'),
    ('programmation', 'Programmation'),
    ('data-ia', 'Data et IA'),
    ('marketing-digital', 'Marketing digital'),
    ('entrepreneuriat-gestion', 'Entrepreneuriat et gestion'),
    ('finance-comptabilite', 'Finance et comptabilité'),
    ('langues', 'Langues'),
    ('bien-etre', 'Bien-être'),
    ('cuisine-artisanat', 'Cuisine et artisanat');

-- Catalogue columns on courses. level stays nullable so a draft can be saved before it is
-- complete; the publish rule requires category, summary and at least one skill.
ALTER TABLE courses
    ADD COLUMN category_id BIGINT NULL,
    ADD COLUMN summary VARCHAR(200) NULL,
    ADD COLUMN level VARCHAR(16) NULL,
    ADD COLUMN language CHAR(2) NOT NULL DEFAULT 'fr',
    ADD COLUMN skills JSON NULL,
    ADD COLUMN requirements JSON NULL,
    ADD COLUMN discount_price DECIMAL(10, 2) NULL,
    ADD COLUMN discount_ends_at DATETIME(6) NULL,
    ADD COLUMN suspended_reason VARCHAR(500) NULL,
    ADD COLUMN rating_avg DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_courses_category FOREIGN KEY (category_id) REFERENCES categories (id),
    ADD CONSTRAINT chk_courses_level
        CHECK (level IS NULL OR level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    ADD CONSTRAINT chk_courses_language CHECK (language IN ('fr', 'ar', 'en')),
    ADD CONSTRAINT chk_courses_discount
        CHECK (discount_price IS NULL OR (discount_price >= 0 AND discount_price < price)),
    ADD CONSTRAINT chk_courses_rating
        CHECK (rating_avg >= 0 AND rating_avg <= 5 AND rating_count >= 0);

-- SUSPENDED is set only by platform moderation; the publisher side is read-only in that status.
ALTER TABLE courses DROP CHECK chk_courses_status;
ALTER TABLE courses ADD CONSTRAINT chk_courses_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'SUSPENDED'));

-- Search uses a FULLTEXT index. utf8mb4_unicode_ci is accent and case insensitive, which the
-- public search tests rely on ("etalonnage" must find "Étalonnage et colorimétrie").
ALTER TABLE courses ADD FULLTEXT INDEX ft_courses_title_summary (title, summary);

-- A lesson now carries a short public description. V1 lessons keep a null description.
ALTER TABLE lessons ADD COLUMN description VARCHAR(1000) NULL;

CREATE TABLE course_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    course_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_tags_course_tag (course_id, tag),
    KEY idx_course_tags_tenant_course (tenant_id, course_id),
    CONSTRAINT chk_course_tags_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT fk_course_tags_course FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Cross-tenant reference data written only by the publisher-event consumer and by the placeholder
-- rule in the studio, read only through crosstenant repositories. Deliberately not @TenantId:
-- a public catalogue query joins these rows for every publisher at once.
CREATE TABLE publisher_snapshots (
    tenant_id CHAR(36) NOT NULL,
    slug VARCHAR(63) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id),
    KEY idx_publisher_snapshots_status (status),
    CONSTRAINT chk_publisher_snapshots_type CHECK (type IN ('INDIVIDUAL', 'ORGANIZATION')),
    CONSTRAINT chk_publisher_snapshots_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE instructor_snapshots (
    user_id BIGINT NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    source_occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_instructor_snapshots_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Enrollment snapshots. V1 rows have no known name or email, so the migration gives them a
-- readable placeholder; every new row carries the values from the JWT or the payment event.
ALTER TABLE enrollments
    ADD COLUMN user_name VARCHAR(120) NULL,
    ADD COLUMN user_email VARCHAR(255) NULL,
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'PAYMENT',
    ADD KEY idx_enrollments_course_enrolled (course_id, enrolled_at),
    ADD CONSTRAINT chk_enrollments_source
        CHECK (source IN ('PAYMENT', 'FREE', 'ADMIN_GRANT'));

UPDATE enrollments
SET user_name = CONCAT('Learner ', user_id),
    user_email = CONCAT('user', user_id, '@unknown.local')
WHERE user_name IS NULL OR user_email IS NULL;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id CHAR(36) NOT NULL,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(120) NOT NULL,
    rating TINYINT NOT NULL,
    comment VARCHAR(2000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    hidden_reason VARCHAR(500) NULL,
    publisher_reply VARCHAR(1000) NULL,
    replied_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_course_user (course_id, user_id),
    KEY idx_reviews_tenant_course_status (tenant_id, course_id, status),
    CONSTRAINT chk_reviews_tenant_not_nil
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    CONSTRAINT chk_reviews_rating CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT chk_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT fk_reviews_course FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Daily view counter. It is a separate row per day so the "views of the last 7 days" trending
-- tie-breaker is one indexed range scan, and so a failed increment can never block a course page.
CREATE TABLE course_views_daily (
    course_id BIGINT NOT NULL,
    day DATE NOT NULL,
    views INT NOT NULL DEFAULT 0,
    PRIMARY KEY (course_id, day),
    CONSTRAINT chk_course_views_nonnegative CHECK (views >= 0),
    CONSTRAINT fk_course_views_course FOREIGN KEY (course_id) REFERENCES courses (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
