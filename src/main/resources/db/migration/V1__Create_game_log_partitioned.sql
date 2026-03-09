-- =====================================================================
-- V1: game_log 파티셔닝 테이블 생성 (주별 파티션)
--     V1 (초기 생성) + V5 (스키마 최적화) + V9 (주별 파티션 전략) 통합
-- =====================================================================

-- 파티션 부모 테이블 생성 (최적화된 스키마로 바로 생성)
CREATE TABLE game_log (
    log_id UUID NOT NULL,
    project_id UUID NOT NULL,
    session_id VARCHAR(128),
    user_id VARCHAR(128),
    severity VARCHAR(20) NOT NULL,
    event_category VARCHAR(32) NOT NULL,
    archive TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trace_id VARCHAR(32),
    span_id VARCHAR(16),
    fingerprint VARCHAR(64) NOT NULL,
    resource JSONB NOT NULL,
    attributes JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (log_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- occurred_at 컬럼 인덱스 (파티션 프루닝 최적화)
CREATE INDEX idx_game_log_occurred_at ON game_log (occurred_at);

-- 주별 파티션 생성 (ISO 주차 기준)
-- 2024년 10주차 (2024-03-04 ~ 2024-03-11)
CREATE TABLE IF NOT EXISTS game_log_2024_w10 PARTITION OF game_log
    FOR VALUES FROM ('2024-03-04 00:00:00+00') TO ('2024-03-11 00:00:00+00');

-- 2024년 11주차 (2024-03-11 ~ 2024-03-18)
CREATE TABLE IF NOT EXISTS game_log_2024_w11 PARTITION OF game_log
    FOR VALUES FROM ('2024-03-11 00:00:00+00') TO ('2024-03-18 00:00:00+00');

-- 2024년 12주차 (2024-03-18 ~ 2024-03-25)
CREATE TABLE IF NOT EXISTS game_log_2024_w12 PARTITION OF game_log
    FOR VALUES FROM ('2024-03-18 00:00:00+00') TO ('2024-03-25 00:00:00+00');

-- 2024년 13주차 (2024-03-25 ~ 2024-04-01)
CREATE TABLE IF NOT EXISTS game_log_2024_w13 PARTITION OF game_log
    FOR VALUES FROM ('2024-03-25 00:00:00+00') TO ('2024-04-01 00:00:00+00');

-- GIN 인덱스 생성 (파티션별)
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w10_attributes ON game_log_2024_w10 USING GIN (attributes jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w10_resource ON game_log_2024_w10 USING GIN (resource jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w10_occurred_at ON game_log_2024_w10 (occurred_at);

CREATE INDEX IF NOT EXISTS idx_game_log_2024_w11_attributes ON game_log_2024_w11 USING GIN (attributes jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w11_resource ON game_log_2024_w11 USING GIN (resource jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w11_occurred_at ON game_log_2024_w11 (occurred_at);

CREATE INDEX IF NOT EXISTS idx_game_log_2024_w12_attributes ON game_log_2024_w12 USING GIN (attributes jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w12_resource ON game_log_2024_w12 USING GIN (resource jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w12_occurred_at ON game_log_2024_w12 (occurred_at);

CREATE INDEX IF NOT EXISTS idx_game_log_2024_w13_attributes ON game_log_2024_w13 USING GIN (attributes jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w13_resource ON game_log_2024_w13 USING GIN (resource jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_game_log_2024_w13_occurred_at ON game_log_2024_w13 (occurred_at);

-- 코멘트 추가
COMMENT ON TABLE game_log IS 'Range Partitioning이 적용된 게임 로그 테이블 (주별 파티션, 28일 후 S3 이동)';
COMMENT ON COLUMN game_log.occurred_at IS '파티션 키: 클라이언트 발생 시각';
COMMENT ON COLUMN game_log.archive IS '로그 본문';
COMMENT ON COLUMN game_log.attributes IS 'GIN 인덱스 적용: 동적 상황 정보 (Performance, Context 등)';
COMMENT ON COLUMN game_log.resource IS 'GIN 인덱스 적용: 정적 환경 정보 (Semantic Convention)';
COMMENT ON COLUMN game_log.fingerprint IS 'SHA-256 해시 (이슈 그룹핑 키)';
COMMENT ON COLUMN game_log.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN game_log.updated_at IS '레코드 수정 시각';
