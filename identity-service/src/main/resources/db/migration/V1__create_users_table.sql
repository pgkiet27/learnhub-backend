CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub       VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    role              VARCHAR(20)  NOT NULL DEFAULT 'student',
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    is_email_verified BOOLEAN      NOT NULL DEFAULT false,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT users_cognito_sub_unique UNIQUE (cognito_sub),
    CONSTRAINT users_email_unique       UNIQUE (email),
    CONSTRAINT users_role_check         CHECK (role IN ('student', 'instructor', 'admin'))
);

-- Không cần CREATE INDEX riêng cho cognito_sub/email: UNIQUE constraint ở trên
-- đã tự tạo sẵn unique B-tree index cho cả 2 cột này.

COMMENT ON TABLE  users              IS 'Bảng lưu thông tin auth của user';
COMMENT ON COLUMN users.cognito_sub  IS 'Sub claim từ Cognito JWT — unique identifier từ AWS';
COMMENT ON COLUMN users.role         IS 'Phân quyền: student | instructor | admin';