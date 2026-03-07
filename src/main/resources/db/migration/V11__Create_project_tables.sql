-- =====================================================================
-- V11: project 도메인 테이블 생성
--      project, project_member, project_api_key
-- =====================================================================

-- ─────────────────────────────────────────────
-- project 테이블 (UuidBaseEntity: UUID PK)
-- ─────────────────────────────────────────────
CREATE TABLE project
(
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id   VARCHAR(20)  NOT NULL,
    company_id  BIGINT       NOT NULL REFERENCES company (id),
    name        VARCHAR(100) NOT NULL,
    profile_key TEXT,
    status      VARCHAR(20)  NOT NULL,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,

    CONSTRAINT uk_project_public_id UNIQUE (public_id)
);

CREATE INDEX idx_project_company_id ON project (company_id);
CREATE INDEX idx_project_status     ON project (status);

-- ─────────────────────────────────────────────
-- project_member 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE project_member
(
    id           BIGSERIAL   PRIMARY KEY,
    project_id   UUID        NOT NULL REFERENCES project (id),
    member_id    UUID        NOT NULL REFERENCES member (id),
    project_role VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    deleted_at   TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL,

    CONSTRAINT uk_project_member UNIQUE (project_id, member_id)
);

CREATE INDEX idx_project_member_project_id ON project_member (project_id);
CREATE INDEX idx_project_member_member_id  ON project_member (member_id);
CREATE INDEX idx_project_member_status     ON project_member (status);

-- ─────────────────────────────────────────────
-- project_api_key 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE project_api_key
(
    id             BIGSERIAL   PRIMARY KEY,
    project_id     UUID        NOT NULL REFERENCES project (id),
    api_key_hash   VARCHAR(64) NOT NULL,
    api_key_status VARCHAR(20) NOT NULL,
    key_prefix     VARCHAR(32) NOT NULL,
    key_last4      VARCHAR(4)  NOT NULL,
    revoked_at     TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL
);

CREATE INDEX idx_project_api_key_project_id ON project_api_key (project_id);
CREATE INDEX idx_project_api_key_hash        ON project_api_key (api_key_hash);
CREATE INDEX idx_project_api_key_status      ON project_api_key (api_key_status);
