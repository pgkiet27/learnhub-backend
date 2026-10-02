ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;

-- One row per user per day with a login or token refresh; feeds the churn features
-- days_since_last_login and login_frequency_trend
CREATE TABLE user_login_days (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    login_date DATE NOT NULL,

    PRIMARY KEY (user_id, login_date)
);

COMMENT ON COLUMN users.last_login_at IS 'Last sync (login) or token refresh';
