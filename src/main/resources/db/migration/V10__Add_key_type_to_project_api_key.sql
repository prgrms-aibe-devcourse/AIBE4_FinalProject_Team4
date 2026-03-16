-- =====================================================================
-- V10: project_api_key 테이블에 key_type 컬럼 및 인덱스 추가
-- =====================================================================

ALTER TABLE project_api_key
    ADD COLUMN key_type VARCHAR(20) NOT NULL DEFAULT 'INGEST';
