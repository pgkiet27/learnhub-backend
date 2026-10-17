CREATE TABLE wishlists (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL,
    course_id  UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT wishlists_user_course_unique UNIQUE (user_id, course_id)
);

CREATE INDEX idx_wishlists_user_id ON wishlists (user_id);