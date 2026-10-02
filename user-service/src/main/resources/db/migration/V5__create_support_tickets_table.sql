CREATE TABLE support_tickets (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    course_id    UUID,                        -- optional: the course the problem is about
    category     VARCHAR(30)  NOT NULL DEFAULT 'other',
    subject      VARCHAR(200) NOT NULL,
    message      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'open',
    admin_reply  TEXT,
    replied_by   UUID,
    replied_at   TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT support_tickets_category_check
        CHECK (category IN ('technical', 'course_content', 'payment', 'account', 'other')),
    CONSTRAINT support_tickets_status_check
        CHECK (status IN ('open', 'in_progress', 'resolved', 'closed'))
);

CREATE INDEX idx_support_tickets_user_created ON support_tickets (user_id, created_at);
CREATE INDEX idx_support_tickets_status ON support_tickets (status, created_at);

COMMENT ON TABLE support_tickets IS 'Help requests from users; the per-user count also feeds the churn feature support_tickets_opened';
