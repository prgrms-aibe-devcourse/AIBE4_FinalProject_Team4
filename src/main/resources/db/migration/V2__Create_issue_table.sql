-- =====================================================================
-- V2: issue 테이블 생성
--     V3 (초기 생성) + V4 (constraint 수정) 통합
-- =====================================================================

CREATE TABLE issue (
    issue_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    fingerprint_quality VARCHAR(20) NOT NULL,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    first_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- fingerprint는 프로젝트별로 고유 (전역 UNIQUE 아님)
    CONSTRAINT unique_fingerprint_per_project UNIQUE (fingerprint, project_id)
);

-- fingerprint + project_id 복합 인덱스 (그룹핑 조회 최적화)
CREATE INDEX idx_issue_fingerprint_project ON issue (fingerprint, project_id);

-- project_id 인덱스 (프로젝트별 이슈 조회)
CREATE INDEX idx_issue_project_id ON issue (project_id);

-- status 인덱스 (상태별 필터링)
CREATE INDEX idx_issue_status ON issue (status);

-- last_occurred_at 인덱스 (최근 발생 순 정렬)
CREATE INDEX idx_issue_last_occurred_at ON issue (last_occurred_at DESC);

-- 코멘트 추가
COMMENT ON TABLE issue IS 'Fingerprint 기반 로그 그룹핑 이슈 테이블';
COMMENT ON COLUMN issue.fingerprint IS 'SHA-256 해시 (이슈 그룹핑 키)';
COMMENT ON COLUMN issue.fingerprint_quality IS '핑거프린트 품질 등급 (HIGH, MEDIUM, LOW, VERY_LOW, FALLBACK)';
COMMENT ON COLUMN issue.occurrence_count IS '동일 이슈 발생 횟수';
COMMENT ON COLUMN issue.status IS 'OPEN, REQUIRES_REVIEW, IN_PROGRESS, RESOLVED, IGNORED';
COMMENT ON CONSTRAINT unique_fingerprint_per_project ON issue IS 'Fingerprint는 프로젝트별로 고유해야 함 (전역 UNIQUE 아님)';
