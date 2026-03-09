-- =====================================================================
-- V5: archive 도메인 테이블 생성 (문서 관리 + RAG)
--     V6 (domain_source) + V7 (document) + V8 (vector_store) 통합
-- =====================================================================

-- PgVector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─────────────────────────────────────────────
-- domain_source 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS domain_source (
    id          BIGSERIAL    PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

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
    embedding_status  VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    uploaded_at       TIMESTAMP    NOT NULL,
    reuploaded_at     TIMESTAMP,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_document_version UNIQUE (document_group_id, major_version, minor_version, patch_version)
);

-- ─────────────────────────────────────────────
-- vector_store 테이블 (Spring AI 기본 스키마 + source_id 커스텀 컬럼)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    source_id BIGINT NOT NULL,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(1536)
);

-- 벡터 유사도 검색용 HNSW 인덱스
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- source_id 기반 일괄 삭제용 인덱스
CREATE INDEX IF NOT EXISTS vector_store_source_id_idx
    ON vector_store (source_id);
