CREATE TABLE coupons (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)   NOT NULL,
    discount_type   VARCHAR(20)   NOT NULL,     -- percent | fixed
    discount_value  DECIMAL(10,2) NOT NULL,     -- % or fixed amount
    min_order_value DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_uses        INTEGER,                    -- NULL = unlimited
    used_count      INTEGER       NOT NULL DEFAULT 0,
    course_id       UUID,                       -- NULL = applies to all courses
    expires_at      TIMESTAMP,                  -- NULL = never expires
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    created_by      UUID          NOT NULL,     -- instructor_id or admin_id
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT coupons_code_unique UNIQUE (code),
    CONSTRAINT coupons_type_check
        CHECK (discount_type IN ('percent', 'fixed')),
    CONSTRAINT coupons_value_check
        CHECK (discount_value > 0),
    CONSTRAINT coupons_percent_check
        CHECK (discount_type != 'percent' OR discount_value <= 100),
    CONSTRAINT coupons_course_fk
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

COMMENT ON COLUMN coupons.course_id     IS 'NULL = applies to all courses';
COMMENT ON COLUMN coupons.discount_type IS 'percent = percentage discount, fixed = fixed amount discount';