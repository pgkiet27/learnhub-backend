CREATE TABLE payments (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID          NOT NULL,   -- UUID from Identity Service
    course_id                   UUID          NOT NULL,   -- UUID from Course Service
    instructor_id               UUID          NOT NULL,

    -- Snapshot of course info at the time the transaction was created
    course_title                VARCHAR(300)  NOT NULL,
    course_thumbnail_url        VARCHAR(500),

    amount                      DECIMAL(10,2) NOT NULL,             -- Total amount the student pays (unit: dollars, not cents)
    currency                    VARCHAR(3)    NOT NULL DEFAULT 'usd',

    -- Revenue split — only computed once status moves to 'succeeded'
    platform_fee_amount         DECIMAL(10,2),                      -- amount * 30%
    instructor_earning_amount   DECIMAL(10,2),                      -- amount * 70%

    stripe_payment_intent_id    VARCHAR(255)  NOT NULL,
    status                      VARCHAR(20)   NOT NULL DEFAULT 'pending',

    paid_at                     TIMESTAMP,

    created_at                  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT payments_stripe_intent_unique UNIQUE (stripe_payment_intent_id),
    CONSTRAINT payments_status_check
        CHECK (status IN ('pending','succeeded','failed','refunded','partially_refunded')),
    CONSTRAINT payments_amount_check CHECK (amount > 0)
);

COMMENT ON TABLE  payments                        IS 'One row per attempt by a student to pay for a course — a student can have multiple rows for the same course if a previous attempt was pending/failed';
COMMENT ON COLUMN payments.instructor_id          IS 'Snapshot taken when the transaction is created — avoids calling Course Service again when computing the revenue split';
COMMENT ON COLUMN payments.amount                 IS 'Unit: dollars (e.g. 49.99) — only converted to cents when calling the Stripe API, never stored as cents in the DB';
COMMENT ON COLUMN payments.status                 IS 'pending | succeeded | failed | refunded | partially_refunded';
COMMENT ON COLUMN payments.stripe_payment_intent_id IS 'Stripe PaymentIntent ID — used by the webhook (Part 3) to look up the matching payment';