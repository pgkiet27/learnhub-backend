CREATE TABLE qa_questions (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id    UUID      NOT NULL,
    course_id    UUID      NOT NULL,    -- Denormalized
    user_id      UUID      NOT NULL,
    content      TEXT      NOT NULL,
    upvote_count INTEGER   NOT NULL DEFAULT 0,
    is_resolved  BOOLEAN   NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT qa_questions_lesson_fk
        FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT qa_questions_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

CREATE TABLE qa_answers (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id  UUID      NOT NULL,
    user_id      UUID      NOT NULL,
    content      TEXT      NOT NULL,
    is_instructor BOOLEAN  NOT NULL DEFAULT false,   -- true = answered by the instructor
    is_accepted  BOOLEAN   NOT NULL DEFAULT false,   -- true = answer accepted
    upvote_count INTEGER   NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT qa_answers_question_fk
        FOREIGN KEY (question_id) REFERENCES qa_questions (id) ON DELETE CASCADE
);

COMMENT ON COLUMN qa_answers.is_instructor IS 'true when the answerer is the course instructor';
COMMENT ON COLUMN qa_answers.is_accepted   IS 'true when the asker marks this answer as correct';