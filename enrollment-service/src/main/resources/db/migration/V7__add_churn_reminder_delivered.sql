ALTER TABLE enrollments ADD COLUMN churn_reminder_delivered_at TIMESTAMP;

COMMENT ON COLUMN enrollments.churn_reminder_delivered_at IS 'Last time a churn reminder email was actually sent (confirmed by notification-service); churn_reminded_at is when it was queued';
