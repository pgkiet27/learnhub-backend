CREATE TABLE sections (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id     UUID         NOT NULL,
    title         VARCHAR(300) NOT NULL,
    description   TEXT,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT sections_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

COMMENT ON TABLE  sections               IS 'Course chapters/sections';
COMMENT ON COLUMN sections.display_order IS 'Display order within the course';