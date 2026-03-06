-- PgVector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─────────────────────────────────────────────
-- vector_store 테이블 (Spring AI 기본 스키마 + source_id 커스텀 컬럼)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    source_id BIGINT NOT NULL,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);

-- 벡터 유사도 검색용 HNSW 인덱스
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- source_id 기반 일괄 삭제용 인덱스
CREATE INDEX IF NOT EXISTS vector_store_source_id_idx
    ON vector_store (source_id);
