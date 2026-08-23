CREATE TABLE courses (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id      UUID          NOT NULL,   -- UUID from Identity Service
    category_id        UUID,
    title              VARCHAR(300)  NOT NULL,
    slug               VARCHAR(300)  NOT NULL,
    description        TEXT,
    short_description  VARCHAR(500),
    thumbnail_url      VARCHAR(500),
    preview_video_url  VARCHAR(500),

    -- Classification
    level              VARCHAR(20)   NOT NULL DEFAULT 'all',
    language           VARCHAR(10)   NOT NULL DEFAULT 'vi',

    -- Pricing
    price              DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount_price     DECIMAL(10,2),

    -- Approval status
    status             VARCHAR(20)   NOT NULL DEFAULT 'draft',
    rejection_reason   TEXT,

    -- Array metadata
    tags               TEXT[]        NOT NULL DEFAULT '{}',
    requirements       TEXT[]        NOT NULL DEFAULT '{}',
    objectives         TEXT[]        NOT NULL DEFAULT '{}',

    -- Stats (updated incrementally)
    total_lessons      INTEGER       NOT NULL DEFAULT 0,
    total_duration     INTEGER       NOT NULL DEFAULT 0,  -- seconds
    total_students     INTEGER       NOT NULL DEFAULT 0,
    total_reviews      INTEGER       NOT NULL DEFAULT 0,
    avg_rating         DECIMAL(3,2)  NOT NULL DEFAULT 0.0,
    is_bestseller      BOOLEAN       NOT NULL DEFAULT false,
    is_featured        BOOLEAN       NOT NULL DEFAULT false,

    published_at       TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT courses_slug_unique UNIQUE (slug),
    CONSTRAINT courses_level_check  CHECK (level IN ('beginner','intermediate','advanced','all')),
    CONSTRAINT courses_status_check CHECK (status IN ('draft','pending','published','rejected','hidden')),
    CONSTRAINT courses_price_check  CHECK (price >= 0),
    CONSTRAINT courses_rating_check CHECK (avg_rating BETWEEN 0 AND 5),

    CONSTRAINT courses_category_fk
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
);

COMMENT ON COLUMN courses.instructor_id   IS 'UUID from Identity/User Service — no FK since it is a different DB';
COMMENT ON COLUMN courses.status          IS 'draft | pending | published | rejected | hidden';
COMMENT ON COLUMN courses.total_duration  IS 'Total duration in seconds';