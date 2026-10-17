CREATE TABLE refresh_tokens (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID      NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    device_info VARCHAR(255),
    ip_address  VARCHAR(45),
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

COMMENT ON TABLE  refresh_tokens            IS 'Lưu refresh tokens để đổi lấy access token mới';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Hash của refresh token (không lưu token thô)';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Thời điểm hết hạn — 30 ngày sau khi tạo';