CREATE TABLE notification_settings (
    id                      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID    NOT NULL,
    email_learning_reminder BOOLEAN NOT NULL DEFAULT true,
    email_promotions        BOOLEAN NOT NULL DEFAULT true,
    email_new_course        BOOLEAN NOT NULL DEFAULT true,
    email_qa_answered       BOOLEAN NOT NULL DEFAULT true,
    push_enabled            BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT notification_settings_user_id_unique UNIQUE (user_id)
);