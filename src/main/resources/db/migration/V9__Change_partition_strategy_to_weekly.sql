-- V6: game_log 파티셔닝 전략 변경: 월별 → 주별
-- 기존 월별 파티션은 유지, 신규 데이터는 주별 파티션에 저장

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

-- GIN 인덱스 생성
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

-- 코멘트 업데이트
COMMENT ON TABLE game_log IS 'Range Partitioning이 적용된 게임 로그 테이블 (주별 파티션, 28일 후 S3 이동)';
