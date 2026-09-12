CREATE TABLE lesson_notes (
    id            UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id     UUID      NOT NULL,
    user_id       UUID      NOT NULL,
    content       TEXT      NOT NULL,
    timestamp_sec INTEGER   NOT NULL DEFAULT 0,

    -- Timestamp in the video when the note was created (seconds)
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT lesson_notes_lesson_fk
        FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
);

COMMENT ON COLUMN lesson_notes.timestamp_sec IS 'Video position when the note was written (seconds)';