CREATE TABLE refunds (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID          NOT NULL,
    stripe_refund_id    VARCHAR(255)  NOT NULL,

    amount              DECIMAL(10,2) NOT NULL,           -- Can be a partial refund, not necessarily equal to payments.amount
    reason               VARCHAR(255),                    -- Reason
    status               VARCHAR(20)   NOT NULL DEFAULT 'pending',

    refunded_at           TIMESTAMP,

    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT refunds_payment_fk
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT refunds_stripe_refund_unique UNIQUE (stripe_refund_id),
    CONSTRAINT refunds_status_check CHECK (status IN ('pending','succeeded','failed')),
    CONSTRAINT refunds_amount_check CHECK (amount > 0)
);

COMMENT ON TABLE  refunds             IS 'A payment can have multiple refunds (repeated partial refunds) until the original amount is fully refunded';
COMMENT ON COLUMN refunds.amount      IS 'Refunded amount — can be less than payments.amount (partial refund)';