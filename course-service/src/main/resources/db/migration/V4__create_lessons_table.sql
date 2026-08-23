CREATE TABLE lessons (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id      UUID         NOT NULL,
    course_id       UUID         NOT NULL,   -- Denormalized for faster queries
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    lesson_type     VARCHAR(20)  NOT NULL DEFAULT 'video',

    -- video: video lesson from S3
    -- document: PDF/Word file from S3
    -- text: plain text content
    video_url       VARCHAR(500),           -- S3 URL after upload
    video_duration  INTEGER,                -- seconds
    document_url    VARCHAR(500),           -- S3 URL for document file
    content         TEXT,                   -- Text content (if lesson_type = text)

    display_order   INTEGER      NOT NULL DEFAULT 0,
    is_preview      BOOLEAN      NOT NULL DEFAULT false,  -- Free preview, no enrollment needed
    is_published    BOOLEAN      NOT NULL DEFAULT false,
    published_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT lessons_section_fk
        FOREIGN KEY (section_id) REFERENCES sections (id) ON DELETE CASCADE,
    CONSTRAINT lessons_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT lessons_type_check
        CHECK (lesson_type IN ('video', 'document', 'text'))
);

COMMENT ON COLUMN lessons.course_id   IS 'Denormalized from section.course_id to avoid an extra JOIN';
COMMENT ON COLUMN lessons.is_preview  IS 'true = viewable even if the user has not purchased the course';