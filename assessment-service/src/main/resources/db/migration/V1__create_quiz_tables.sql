CREATE TABLE quizzes (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID         NOT NULL,   -- UUID from Course Service
    lesson_id       UUID,                    -- null = end-of-course quiz
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    pass_score      SMALLINT     NOT NULL DEFAULT 70,
    time_limit_sec  INTEGER,                 -- null = no time limit
    max_attempts    INTEGER      DEFAULT 3,  -- null = unlimited
    is_ai_generated BOOLEAN      NOT NULL DEFAULT false,
    is_published    BOOLEAN      NOT NULL DEFAULT false,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT quizzes_pass_score_check CHECK (pass_score BETWEEN 0 AND 100),
    CONSTRAINT quizzes_time_limit_check CHECK (time_limit_sec IS NULL OR time_limit_sec > 0),
    CONSTRAINT quizzes_max_attempts_check CHECK (max_attempts IS NULL OR max_attempts > 0)
);

CREATE TABLE questions (
    id            UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id       UUID      NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    content       TEXT      NOT NULL,
    explanation   TEXT,                      -- shown after submitting
    display_order INTEGER   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE question_options (
    id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id   UUID    NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    content       TEXT    NOT NULL,
    is_correct    BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0
);

-- No cascade: attempts are history (and feed the churn feature quiz_failure_count)
CREATE TABLE quiz_attempts (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id        UUID         NOT NULL REFERENCES quizzes (id),
    course_id      UUID         NOT NULL,   -- denormalized for per-course counts
    user_id        UUID         NOT NULL,
    score          DECIMAL(5,2) NOT NULL,   -- %
    is_passed      BOOLEAN      NOT NULL,
    answers        JSONB        NOT NULL,   -- graded snapshot, so results survive later quiz edits
    time_spent_sec INTEGER,
    started_at     TIMESTAMP    NOT NULL,
    submitted_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quizzes_course ON quizzes (course_id);
CREATE INDEX idx_questions_quiz ON questions (quiz_id);
CREATE INDEX idx_question_options_question ON question_options (question_id);
CREATE INDEX idx_quiz_attempts_quiz_user ON quiz_attempts (quiz_id, user_id);
CREATE INDEX idx_quiz_attempts_user_course ON quiz_attempts (user_id, course_id);
