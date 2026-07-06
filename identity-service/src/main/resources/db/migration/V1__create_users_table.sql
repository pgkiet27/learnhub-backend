CREATE TABLE IF NOT EXISTS users (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub       VARCHAR(255) UNIQUE NOT NULL,
    email             VARCHAR(255) UNIQUE NOT NULL,
    role              VARCHAR(20)  NOT NULL DEFAULT 'student'
                      CONSTRAINT chk_role CHECK (role IN ('student', 'instructor', 'admin')),
    is_active         BOOLEAN     NOT NULL DEFAULT true,
    is_email_verified BOOLEAN     NOT NULL DEFAULT false,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_cognito_sub ON users(cognito_sub);