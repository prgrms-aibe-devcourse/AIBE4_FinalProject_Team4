-- V5: game_log 테이블 스키마 최적화
-- ERD 구조와 일치시키기 위한 스키마 변경

-- 1. project_id 타입 변경 (VARCHAR → UUID)
ALTER TABLE game_log ALTER COLUMN project_id TYPE UUID USING project_id::uuid;

-- 2. session_id, user_id 크기 최적화
ALTER TABLE game_log ALTER COLUMN session_id TYPE VARCHAR(128);
ALTER TABLE game_log ALTER COLUMN user_id TYPE VARCHAR(128);

-- 3. severity, event_category 크기 최적화
ALTER TABLE game_log ALTER COLUMN severity TYPE VARCHAR(20);
ALTER TABLE game_log ALTER COLUMN event_category TYPE VARCHAR(32);

-- 4. body 컬럼명 변경 → archive
ALTER TABLE game_log RENAME COLUMN body TO archive;

-- 5. trace_id, span_id 크기 최적화
ALTER TABLE game_log ALTER COLUMN trace_id TYPE VARCHAR(32);
ALTER TABLE game_log ALTER COLUMN span_id TYPE VARCHAR(16);

-- 6. fingerprint 타입 변경 및 NOT NULL 제약 추가
ALTER TABLE game_log ALTER COLUMN fingerprint TYPE VARCHAR(64);
ALTER TABLE game_log ALTER COLUMN fingerprint SET NOT NULL;

-- 7. created_at, updated_at 컬럼 추가
ALTER TABLE game_log ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE game_log ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- 코멘트 추가
COMMENT ON COLUMN game_log.archive IS '로그 본문 (기존 body 컬럼에서 변경)';
COMMENT ON COLUMN game_log.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN game_log.updated_at IS '레코드 수정 시각';
