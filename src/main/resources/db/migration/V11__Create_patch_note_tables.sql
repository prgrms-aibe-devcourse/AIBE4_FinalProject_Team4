-- =====================================================================
-- V11: pending_item, patch_note 테이블 추가
-- =====================================================================

-- pg_bigm 확장이 설치되지 않은 경우 설치 (GIN 인덱스 사용을 위해 필수)
CREATE EXTENSION IF NOT EXISTS pg_bigm;

-- ----------------------------------------------------------------------------
-- 1. pending_item
-- ----------------------------------------------------------------------------
CREATE TABLE pending_item (
    id                BIGSERIAL    PRIMARY KEY,
    project_id        UUID         NOT NULL,
    source_id         BIGINT       NOT NULL,
    source_type       VARCHAR(50)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    summary           TEXT         NOT NULL,
    choseong          VARCHAR(255) NOT NULL DEFAULT '',
    patch_type        VARCHAR(50)  NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    is_source_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    source_created_at TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_pending_item_project_source
        UNIQUE (project_id, source_type, source_id),

    CONSTRAINT chk_pending_item_source_type
        CHECK (source_type IN ('ISSUE', 'DOCUMENT')),
    CONSTRAINT chk_pending_item_patch_type
        CHECK (patch_type IN ('NEW', 'CHANGE', 'FIX', 'MAINTENANCE')),
    CONSTRAINT chk_pending_item_status
        CHECK (status IN ('PENDING', 'EXCLUDED', 'COMPLETED'))
);

-- 기본 조회
CREATE INDEX idx_pending_item_project_status
    ON pending_item (project_id, status);
CREATE INDEX idx_pending_item_project_source_created_at
    ON pending_item (project_id, source_created_at DESC);
CREATE INDEX idx_pending_item_project_patch_type
    ON pending_item (project_id, patch_type);

-- 키워드 검색 (pg_bigm GIN)
CREATE INDEX idx_pending_item_title_bigm
    ON pending_item USING gin (title gin_bigm_ops);
CREATE INDEX idx_pending_item_summary_bigm
    ON pending_item USING gin (summary gin_bigm_ops);

-- 초성 검색
CREATE INDEX idx_pending_item_choseong
    ON pending_item (choseong);

-- 원본 삭제 항목 조회 최적화 (partial index)
CREATE INDEX idx_pending_item_source_deleted
    ON pending_item (project_id, is_source_deleted)
    WHERE is_source_deleted = TRUE;

-- ----------------------------------------------------------------------------
-- 2. patch_note
-- ----------------------------------------------------------------------------
CREATE TABLE patch_note (
    id            BIGSERIAL    PRIMARY KEY,
    project_id    UUID         NOT NULL,
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    major_version INT          NOT NULL,
    minor_version INT          NOT NULL,
    patch_version INT          NOT NULL,
    deleted_at    TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_patch_note_project_version
        UNIQUE (project_id, major_version, minor_version, patch_version),

    CONSTRAINT chk_patch_note_version_range
        CHECK (major_version >= 0 AND minor_version >= 0 AND patch_version >= 0),
    CONSTRAINT chk_patch_note_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DELETED'))
);

-- 기본 조회
CREATE INDEX idx_patch_note_project_status
    ON patch_note (project_id, status);
CREATE INDEX idx_patch_note_project_created_at
    ON patch_note (project_id, created_at DESC);

-- 활성 항목만 대상으로 하는 partial index (soft delete 최적화)
CREATE INDEX idx_patch_note_project_active
    ON patch_note (project_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- 3. vector_store 추가 인덱스
-- source_id 컬럼이 전용 BIGINT 컬럼으로 존재하므로
-- metadata JSONB 기반 source_id 필터 인덱스는 불필요.
-- project_id는 metadata JSONB에서 조회하므로 아래 인덱스 추가.
-- ----------------------------------------------------------------------------

-- metadata 내 project_id 필터용
-- RAG retrieval 시 project_id 격리에 사용
CREATE INDEX idx_vector_store_project_id
    ON vector_store ((metadata->>'project_id'));

CREATE INDEX idx_vector_store_source_type
    ON vector_store ((metadata->>'source_type'));

-- 하이브리드 서치 키워드 검색용 (pg_bigm GIN)
CREATE INDEX idx_vector_store_content_bigm
    ON vector_store USING gin (content gin_bigm_ops);
