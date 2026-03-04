-- V1: game_log 테이블을 Range Partitioning으로 생성
-- 파티션 키: occurred_at (월별 파티션)

-- 파티션 부모 테이블 생성
CREATE TABLE game_log (
    log_id UUID NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    severity VARCHAR(50) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trace_id VARCHAR(255),
    span_id VARCHAR(255),
    fingerprint VARCHAR(255),
    resource JSONB NOT NULL,
    attributes JSONB NOT NULL,
    PRIMARY KEY (log_id, occurred_at)  -- 파티션 키를 PRIMARY KEY에 포함
) PARTITION BY RANGE (occurred_at);

-- occurred_at 컬럼에 인덱스 (파티션 프루닝 최적화)
CREATE INDEX idx_game_log_occurred_at ON game_log (occurred_at);

-- 초기 파티션 생성 (현재 월 기준 -1개월 ~ +2개월)
-- 2024년 2월 파티션
CREATE TABLE game_log_2024_02 PARTITION OF game_log
    FOR VALUES FROM ('2024-02-01 00:00:00+00') TO ('2024-03-01 00:00:00+00');

-- 2024년 3월 파티션
CREATE TABLE game_log_2024_03 PARTITION OF game_log
    FOR VALUES FROM ('2024-03-01 00:00:00+00') TO ('2024-04-01 00:00:00+00');

-- 2024년 4월 파티션
CREATE TABLE game_log_2024_04 PARTITION OF game_log
    FOR VALUES FROM ('2024-04-01 00:00:00+00') TO ('2024-05-01 00:00:00+00');

-- 2024년 5월 파티션 (미래 데이터 대비)
CREATE TABLE game_log_2024_05 PARTITION OF game_log
    FOR VALUES FROM ('2024-05-01 00:00:00+00') TO ('2024-06-01 00:00:00+00');

-- GIN 인덱스 생성 (jsonb_path_ops 전략)
-- attributes 컬럼: 동적 상황 정보 검색 최적화
CREATE INDEX idx_game_log_2024_02_attributes ON game_log_2024_02 USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_game_log_2024_03_attributes ON game_log_2024_03 USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_game_log_2024_04_attributes ON game_log_2024_04 USING GIN (attributes jsonb_path_ops);
CREATE INDEX idx_game_log_2024_05_attributes ON game_log_2024_05 USING GIN (attributes jsonb_path_ops);

-- resource 컬럼: 정적 환경 정보 검색 최적화
CREATE INDEX idx_game_log_2024_02_resource ON game_log_2024_02 USING GIN (resource jsonb_path_ops);
CREATE INDEX idx_game_log_2024_03_resource ON game_log_2024_03 USING GIN (resource jsonb_path_ops);
CREATE INDEX idx_game_log_2024_04_resource ON game_log_2024_04 USING GIN (resource jsonb_path_ops);
CREATE INDEX idx_game_log_2024_05_resource ON game_log_2024_05 USING GIN (resource jsonb_path_ops);

-- 파티션별 occurred_at 인덱스 (시간 범위 쿼리 최적화)
CREATE INDEX idx_game_log_2024_02_occurred_at ON game_log_2024_02 (occurred_at);
CREATE INDEX idx_game_log_2024_03_occurred_at ON game_log_2024_03 (occurred_at);
CREATE INDEX idx_game_log_2024_04_occurred_at ON game_log_2024_04 (occurred_at);
CREATE INDEX idx_game_log_2024_05_occurred_at ON game_log_2024_05 (occurred_at);

-- 코멘트 추가
COMMENT ON TABLE game_log IS 'Range Partitioning이 적용된 게임 로그 테이블 (월별 파티션)';
COMMENT ON COLUMN game_log.occurred_at IS '파티션 키: 클라이언트 발생 시각';
COMMENT ON COLUMN game_log.attributes IS 'GIN 인덱스 적용: 동적 상황 정보 (Performance, Context 등)';
COMMENT ON COLUMN game_log.resource IS 'GIN 인덱스 적용: 정적 환경 정보 (Semantic Convention)';
