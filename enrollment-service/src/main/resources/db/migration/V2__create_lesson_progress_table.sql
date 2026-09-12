CREATE TABLE lesson_progress (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id       UUID        NOT NULL,
    user_id             UUID        NOT NULL,   -- Denormalized from enrollments.user_id for faster queries
    lesson_id           UUID        NOT NULL,   -- UUID from Course Service, cannot be an FK (different DB)

    is_completed         BOOLEAN    NOT NULL DEFAULT false,
    watch_duration_sec    INTEGER   NOT NULL DEFAULT 0,  -- Highest watch time (in seconds) ever reached
    last_position_sec     INTEGER   NOT NULL DEFAULT 0,  -- Last playback position, used for "resume watching"
    completed_at           TIMESTAMP,

    created_at              TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT lesson_progress_enrollment_fk
        FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE,
    CONSTRAINT lesson_progress_unique UNIQUE (enrollment_id, lesson_id)
);

COMMENT ON TABLE  lesson_progress                  IS 'Per-lesson viewing progress for a specific enrollment';
COMMENT ON COLUMN lesson_progress.watch_duration_sec IS 'Highest value ever reached — does not decrease when the student rewinds a video already watched';
COMMENT ON COLUMN lesson_progress.last_position_sec  IS 'Most recent playback position — always overwritten, used to resume the video';