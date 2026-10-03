-- Training data for the churn model: the features of each scoring run, labelled 14 days later
CREATE TABLE churn_feature_snapshots (
    snapshot_date                DATE          NOT NULL,   -- Asia/Ho_Chi_Minh date of the run
    enrollment_id                UUID          NOT NULL,
    snapshot_at                  TIMESTAMP     NOT NULL,
    user_id                      UUID          NOT NULL,
    course_id                    UUID          NOT NULL,
    enrolled_at                  TIMESTAMP     NOT NULL,

    -- Model features, NULL when unavailable
    current_course_progress      DOUBLE PRECISION,
    days_since_last_lesson       DOUBLE PRECISION,
    watch_percentage_last_week   DOUBLE PRECISION,
    days_to_complete_last_lesson DOUBLE PRECISION,
    days_since_last_login        DOUBLE PRECISION,
    login_frequency_trend        DOUBLE PRECISION,
    quiz_failure_count           DOUBLE PRECISION,
    support_tickets_opened       DOUBLE PRECISION,

    missing_sources              VARCHAR(100),
    churn_score                  DECIMAL(5,4),
    risk_level                   VARCHAR(10),
    model_used                   VARCHAR(50),
    reminder_queued              BOOLEAN       NOT NULL DEFAULT false,

    churn                        SMALLINT,
    reminded_in_window           BOOLEAN,
    labeled_at                   TIMESTAMP,

    PRIMARY KEY (snapshot_date, enrollment_id),
    CONSTRAINT churn_feature_snapshots_churn_check CHECK (churn IN (0, 1))
);

CREATE INDEX idx_churn_snapshots_unlabeled ON churn_feature_snapshots (snapshot_at) WHERE labeled_at IS NULL;

COMMENT ON COLUMN churn_feature_snapshots.missing_sources    IS 'Services that failed during the run (identity, user, assessment, ai); their features are NULL for a technical reason';
COMMENT ON COLUMN churn_feature_snapshots.churn              IS '1 = no lesson activity within 14 days after snapshot_at and course not completed; NULL = could not be determined';
COMMENT ON COLUMN churn_feature_snapshots.reminder_queued    IS 'This run queued a reminder email; it may still not be sent (opted out, no email)';
COMMENT ON COLUMN churn_feature_snapshots.reminded_in_window IS 'A churn reminder email was actually sent within the label window, which may have changed the outcome';
