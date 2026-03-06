-- V2: issue 테이블 생성
-- fingerprint 기반 로그 그룹핑을 위한 이슈 테이블

CREATE TABLE issue (
    issue_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 해시 (64자)
    title VARCHAR(500) NOT NULL,              -- 이슈 제목
    status VARCHAR(20) NOT NULL,              -- OPEN, REQUIRES_REVIEW, IN_PROGRESS, RESOLVED, IGNORED
    severity VARCHAR(20) NOT NULL,            -- ERROR, WARN, INFO 등
    fingerprint_quality VARCHAR(20) NOT NULL, -- HIGH, MEDIUM, LOW, VERY_LOW, FALLBACK
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    first_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
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
COMMENT ON COLUMN issue.fingerprint_quality IS '핑거프린트 품질 등급';
COMMENT ON COLUMN issue.occurrence_count IS '동일 이슈 발생 횟수';
COMMENT ON COLUMN issue.status IS 'LOW/VERY_LOW 품질은 REQUIRES_REVIEW 상태로 생성';
