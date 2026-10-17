CREATE TABLE user_profiles (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    full_name    VARCHAR(100) NOT NULL DEFAULT '',
    avatar_url   VARCHAR(500),
    bio          TEXT,
    headline     VARCHAR(200),
    website_url  VARCHAR(255),
    phone_number VARCHAR(20),
    language     VARCHAR(10)  NOT NULL DEFAULT 'vi',
    timezone     VARCHAR(50)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT user_profiles_user_id_unique UNIQUE (user_id)
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);

COMMENT ON TABLE  user_profiles          IS 'Thông tin profile của user';
COMMENT ON COLUMN user_profiles.user_id  IS 'UUID từ Identity Service — không phải FK vì khác DB';