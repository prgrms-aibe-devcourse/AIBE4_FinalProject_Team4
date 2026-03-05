-- =====================================================================
-- V2: company / member 테이블 생성
-- =====================================================================

-- ─────────────────────────────────────────────
-- company 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE company
(
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    profile_key TEXT,
    status      VARCHAR(20)  NOT NULL,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- ─────────────────────────────────────────────
-- member 테이블 (UuidBaseEntity: UUID PK)
-- ─────────────────────────────────────────────
CREATE TABLE member
(
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     BIGINT       REFERENCES company (id),
    email          VARCHAR(150) NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    nickname       VARCHAR(20)  NOT NULL,
    global_role    VARCHAR(20)  NOT NULL,
    position       VARCHAR(20)  NOT NULL DEFAULT 'JUNIOR',
    profile_key    TEXT,
    provider       VARCHAR(20)  NOT NULL,
    provider_id    VARCHAR(255) NOT NULL,
    account_status VARCHAR(20)  NOT NULL,
    deleted_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,

    CONSTRAINT uk_member_provider UNIQUE (provider, provider_id)
);
