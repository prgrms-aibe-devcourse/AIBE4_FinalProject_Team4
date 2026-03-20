CREATE TABLE issue_alert_rule (
    id              BIGSERIAL    PRIMARY KEY,
    member_id       UUID         NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    project_id      UUID         NOT NULL,
    notify_critical BOOLEAN      NOT NULL DEFAULT true,
    notify_high     BOOLEAN      NOT NULL DEFAULT true,
    notify_medium   BOOLEAN      NOT NULL DEFAULT true,
    notify_low      BOOLEAN      NOT NULL DEFAULT true,
    notify_assignee BOOLEAN      NOT NULL DEFAULT true,
    notify_status   BOOLEAN      NOT NULL DEFAULT true,
    notify_comment  BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_alert_rule UNIQUE (member_id, project_id)
);
