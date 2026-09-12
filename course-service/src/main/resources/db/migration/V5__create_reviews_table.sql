CREATE TABLE reviews (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id    UUID      NOT NULL,
    user_id      UUID      NOT NULL,   -- UUID from Identity Service
    rating       SMALLINT  NOT NULL,
    comment      TEXT,
    is_visible   BOOLEAN   NOT NULL DEFAULT true,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT reviews_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT reviews_rating_check
        CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT reviews_unique_user_course
        UNIQUE (course_id, user_id)
    -- Each user can only review a course once
);

COMMENT ON COLUMN reviews.is_visible IS 'false = review hidden by admin for violating rules';