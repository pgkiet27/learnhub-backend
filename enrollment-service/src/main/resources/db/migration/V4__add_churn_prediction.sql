-- Needed to compute watch_percentage_last_week; only filled for progress updates after this migration
ALTER TABLE lesson_progress ADD COLUMN video_duration_sec INTEGER;

ALTER TABLE enrollments
    ADD COLUMN churn_score        DECIMAL(5,4),
    ADD COLUMN churn_risk_level   VARCHAR(10),
    ADD COLUMN churn_predicted_at TIMESTAMP,
    ADD COLUMN churn_reminded_at  TIMESTAMP,
    ADD CONSTRAINT enrollments_churn_risk_check CHECK (churn_risk_level IN ('low', 'medium', 'high'));

CREATE INDEX idx_enrollments_course_risk ON enrollments (course_id, churn_risk_level);

COMMENT ON COLUMN enrollments.churn_score       IS 'Probability (0-1) of dropping out, from ai-service; refreshed by the daily churn job';
COMMENT ON COLUMN enrollments.churn_reminded_at IS 'Last reminder email trigger — used as a cooldown so students are not spammed';
