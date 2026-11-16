-- V3: store the course counters that the catalogue used to count per row.
--
-- student_count did not exist before: the public catalogue counted ACTIVE enrollments with a
-- correlated subquery on every card. It is now a column maintained by the two write paths
-- (free enrollment and the payment consumer), so a read never counts enrollments itself.
ALTER TABLE courses
    ADD COLUMN student_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_courses_student_count CHECK (student_count >= 0);

-- Backfill every counter from its source rows. The same definition is recomputed in Java
-- (CourseStatisticsService) after a review or an enrollment write:
--   rating_avg / rating_count -> VISIBLE reviews only
--   student_count             -> ACTIVE enrollments only
-- tenant_id is repeated in each predicate because the counters are tenant data and a course id is
-- only unique per database, never assume the two tables share a stray row.
UPDATE courses c
SET c.rating_avg = COALESCE((SELECT ROUND(AVG(r.rating), 2)
                             FROM reviews r
                             WHERE r.course_id = c.id
                               AND r.tenant_id = c.tenant_id
                               AND r.status = 'VISIBLE'), 0.00),
    c.rating_count = (SELECT COUNT(*)
                      FROM reviews r
                      WHERE r.course_id = c.id
                        AND r.tenant_id = c.tenant_id
                        AND r.status = 'VISIBLE'),
    c.student_count = (SELECT COUNT(*)
                       FROM enrollments e
                       WHERE e.course_id = c.id
                         AND e.tenant_id = c.tenant_id
                         AND e.status = 'ACTIVE');
