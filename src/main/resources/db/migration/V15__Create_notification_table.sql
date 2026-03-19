CREATE TABLE notification (
    id          BIGSERIAL                   PRIMARY KEY,
    receiver_id UUID                        NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    source_id   BIGINT                      NOT NULL REFERENCES domain_source(id) ON DELETE CASCADE,
    event_type  VARCHAR(50)                 NOT NULL,
    title       VARCHAR(255)               NOT NULL,
    message     TEXT                       NOT NULL,
    severity    VARCHAR(20)                DEFAULT 'LOW',
    is_toast    BOOLEAN                    NOT NULL DEFAULT false,
    is_read     BOOLEAN                    NOT NULL DEFAULT false,
    is_ignored  BOOLEAN                    NOT NULL DEFAULT false,
    related_url VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE   NOT NULL
);

-- 목록 조회 (receiver_id + 최신순)
CREATE INDEX idx_notification_receiver_id
    ON notification (receiver_id, created_at DESC);

-- 배지 카운트 (partial index — 읽힌 알림 제외)
CREATE INDEX idx_notification_receiver_unread
    ON notification (receiver_id)
    WHERE is_read = false AND is_ignored = false;

-- domain_source JOIN 지원
CREATE INDEX idx_notification_source_id ON notification (source_id);
