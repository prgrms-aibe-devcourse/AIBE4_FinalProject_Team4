-- V7: 3-Tier 저장 전략을 위한 Tablespace 생성
-- Hot Storage (SSD): 0~7일 데이터 - 빠른 읽기/쓰기
-- Warm Storage (HDD): 7~28일 데이터 - 저비용 보관

-- ⚠️ 주의: 실제 운영 환경에서는 스토리지 경로를 서버 실제 경로로 수정 필요
-- 로컬 개발 환경에서는 기본 tablespace 사용 (생성 skip)

-- Hot Storage Tablespace (SSD 마운트 경로)
-- CREATE TABLESPACE hot_storage
--     OWNER postgres
--     LOCATION '/mnt/ssd/postgres/hot';

-- Warm Storage Tablespace (HDD 마운트 경로)
-- CREATE TABLESPACE warm_storage
--     OWNER postgres
--     LOCATION '/mnt/hdd/postgres/warm';

-- 코멘트 추가
-- COMMENT ON TABLESPACE hot_storage IS '3-Tier Hot Storage: 0~7일 데이터 (SSD)';
-- COMMENT ON TABLESPACE warm_storage IS '3-Tier Warm Storage: 7~28일 데이터 (HDD)';

-- ✅ 개발 환경 참고 사항:
-- 1. 로컬 개발 시에는 기본 tablespace(pg_default) 사용
-- 2. 운영 환경 배포 시:
--    a. 서버에 SSD/HDD 마운트 확인
--    b. PostgreSQL이 해당 디렉토리에 쓰기 권한 부여
--    c. 위 CREATE TABLESPACE 주석 해제 후 실행

-- 임시로 설정 정보만 저장 (테이블 생성)
CREATE TABLE IF NOT EXISTS partition_tier_config (
    tier_name VARCHAR(10) PRIMARY KEY,
    tablespace_name VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

INSERT INTO partition_tier_config (tier_name, tablespace_name, description) VALUES
    ('hot', 'pg_default', '0~7일 데이터 (개발: pg_default, 운영: hot_storage)'),
    ('warm', 'pg_default', '7~28일 데이터 (개발: pg_default, 운영: warm_storage)')
ON CONFLICT (tier_name) DO NOTHING;

COMMENT ON TABLE partition_tier_config IS '3-Tier 저장 전략 설정 (운영 시 tablespace_name 수정 필요)';
