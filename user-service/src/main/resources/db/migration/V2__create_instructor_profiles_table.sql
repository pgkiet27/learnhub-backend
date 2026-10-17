CREATE TABLE instructor_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    title            VARCHAR(200),
    expertise        TEXT[]       NOT NULL DEFAULT '{}',
    experience_years INTEGER      NOT NULL DEFAULT 0,
    total_students   INTEGER      NOT NULL DEFAULT 0,
    total_courses    INTEGER      NOT NULL DEFAULT 0,
    total_revenue    DECIMAL(15,2) NOT NULL DEFAULT 0,
    rating           DECIMAL(3,2) NOT NULL DEFAULT 0.0,
    bank_account     VARCHAR(50),
    bank_name        VARCHAR(100),
    is_verified      BOOLEAN      NOT NULL DEFAULT false,
    verified_at      TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT instructor_profiles_user_id_unique UNIQUE (user_id)
);

CREATE INDEX idx_instructor_profiles_user_id ON instructor_profiles (user_id);