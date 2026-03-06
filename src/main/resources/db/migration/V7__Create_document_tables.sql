-- ─────────────────────────────────────────────
-- document_group 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS document_group (
    id         BIGSERIAL    PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES project (id),
    category   VARCHAR(10)  NOT NULL,
    group_name VARCHAR(30)  NOT NULL,
    choseong   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_document_group UNIQUE (project_id, category, group_name)
);

-- ─────────────────────────────────────────────
-- document_metadata 테이블 (PK = domain_source.id 공유)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS document_metadata (
    id                BIGINT       PRIMARY KEY REFERENCES domain_source (id),
    document_group_id BIGINT       NOT NULL REFERENCES document_group (id),
    document_name     VARCHAR(255) NOT NULL,
    choseong          VARCHAR(255) NOT NULL,
    extension         VARCHAR(10) NOT NULL,
    major_version     INT          NOT NULL,
    minor_version     INT          NOT NULL,
    patch_version     INT          NOT NULL,
    hash              VARCHAR(64)  NOT NULL,
    size              BIGINT       NOT NULL,
    stored_key        VARCHAR(255) NOT NULL,
    is_processed      BOOLEAN      NOT NULL,
    uploaded_at       TIMESTAMP    NOT NULL,
    reuploaded_at     TIMESTAMP,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_document_version UNIQUE (document_group_id, major_version, minor_version, patch_version)
);
