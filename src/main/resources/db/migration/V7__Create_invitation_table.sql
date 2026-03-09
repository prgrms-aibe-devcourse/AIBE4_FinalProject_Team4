-- =====================================================================
-- V7: invitation 테이블 생성
-- =====================================================================

CREATE TABLE invitation
(
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID         NOT NULL REFERENCES project (id),
    inviter_id   UUID         NOT NULL REFERENCES member (id),
    member_id    UUID                     REFERENCES member (id),
    target_email VARCHAR(150) NOT NULL,
    target_role  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used_at      TIMESTAMP,
    revoked_at   TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_invitation_project_email_status ON invitation (project_id, target_email, status);
CREATE INDEX idx_invitation_target_email ON invitation (target_email);
CREATE INDEX idx_invitation_status_expires_at ON invitation (status, expires_at);
