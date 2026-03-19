-- ============================================================
-- V16: pending_item에 diff 기반 패치노트 생성 필드 추가
-- ============================================================
-- 배경:
--   문서 업데이트 시 1개 문서가 N개의 변경 항목(pending_item)을 생성한다.
--   기존 UK (project_id, source_type, source_id) → change_index 포함으로 확장.
--   신규 문서 및 이슈 항목은 change_index = 0 (기본값).
-- ============================================================

-- 1. 신규 컬럼 추가
ALTER TABLE pending_item
    ADD COLUMN IF NOT EXISTS change_index INTEGER NOT NULL DEFAULT 0;

-- 2. 변경 근거 텍스트 (diff 원문, RAG 컨텍스트로 직접 사용)
ALTER TABLE pending_item
    ADD COLUMN IF NOT EXISTS evidence TEXT;

-- 3. 패치노트 포함 우선순위 점수 (0.0 ~ 1.0)
ALTER TABLE pending_item
    ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION;

-- 4. 기존 UK 제거
ALTER TABLE pending_item
    DROP CONSTRAINT IF EXISTS uk_pending_item_project_source;

-- 5. change_index 포함 신규 UK 생성
ALTER TABLE pending_item
    ADD CONSTRAINT uk_pending_item_project_source_change
        UNIQUE (project_id, source_type, source_id, change_index);

-- 6. change_index 검색용 인덱스
CREATE INDEX IF NOT EXISTS idx_pending_item_source_change
    ON pending_item (project_id, source_type, source_id, change_index);
