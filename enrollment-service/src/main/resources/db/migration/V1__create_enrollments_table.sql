CREATE TABLE enrollments (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID          NOT NULL,   -- UUID from Identity Service
    course_id             UUID          NOT NULL,   -- UUID from Course Service

    course_title          VARCHAR(300)  NOT NULL,
    course_thumbnail_url  VARCHAR(500),
    total_lessons         INTEGER       NOT NULL DEFAULT 0,

    -- Cached aggregate
    completed_lessons     INTEGER       NOT NULL DEFAULT 0,
    progress_percent      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    is_completed           BOOLEAN      NOT NULL DEFAULT false,

    enrolled_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    last_accessed_at        TIMESTAMP,   -- Last time the student viewed any lesson (used to sort "Continue learning")

    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT enrollments_unique_user_course UNIQUE (user_id, course_id),
    CONSTRAINT enrollments_progress_check CHECK (progress_percent BETWEEN 0 AND 100)
);

COMMENT ON TABLE  enrollments                  IS 'Which courses a student has enrolled in and their overall progress';
COMMENT ON COLUMN enrollments.course_title     IS 'Snapshot taken at enrollment time — avoids calling back to Course Service when rendering the My Learning page';
COMMENT ON COLUMN enrollments.total_lessons    IS 'Snapshot of the total published lessons at enrollment time — used as the denominator for progress_percent';
COMMENT ON COLUMN enrollments.last_accessed_at IS 'Updated whenever the update-progress API is called — used to sort "Recently continued"';