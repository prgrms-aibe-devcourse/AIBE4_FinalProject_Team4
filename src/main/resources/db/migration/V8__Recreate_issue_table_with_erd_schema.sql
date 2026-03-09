-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- V8: Issue 테이블 재생성 (ERD 기준)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 목적: ERD 설계와 Entity 동기화
-- 주요 변경:
--   - PK: UUID (issue_id) → BIGINT (id) - 성능 및 ERD 표준 반영
--   - 필드 추가: assignee_id, description, severity_score, error_type, stack_key, priority 등
--   - Status 값: OPEN → TODO
--
-- ⚠️ DROP TABLE 사용 이유:
--   - PK 타입 변경(UUID → BIGINT)으로 인해 ALTER TABLE 불가
--   - V2 migration은 이미 팀원들에게 배포되어 수정 불가 (Flyway checksum)
--   - 현재 개발 단계로 운영 데이터 없음
--   - 로컬 DB 초기화: docker compose down -v && docker compose up -d
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- 기존 테이블 삭제 (PK 타입 변경으로 인한 불가피한 조치)
DROP TABLE IF EXISTS issue CASCADE;

-- Issue 테이블 생성 (ERD 기준)
CREATE TABLE issue (
    id                  BIGSERIAL                   PRIMARY KEY,
    assignee_id         UUID                        NOT NULL,
    project_id          UUID                        NOT NULL,
    title               VARCHAR(255)                NOT NULL,
    description         TEXT                        NULL,
    fingerprint         VARCHAR(64)                 NOT NULL,
    issue_type          VARCHAR(50)                 DEFAULT 'BUG',
    status              VARCHAR(50)                 NOT NULL DEFAULT 'TODO',
    priority            VARCHAR(50)                 NULL,
    severity            VARCHAR(20)                 NOT NULL DEFAULT 'LOW',
    severity_score      INTEGER                     NOT NULL DEFAULT 0,
    error_type          VARCHAR(100)                NOT NULL DEFAULT 'UNKNOWN',
    stack_key           VARCHAR(255)                NULL,
    occurrence_count    INTEGER                     NOT NULL DEFAULT 1,
    resolution_note     TEXT                        NULL,
    first_occurred_at   TIMESTAMP WITH TIME ZONE    NOT NULL,
    last_occurred_at    TIMESTAMP WITH TIME ZONE    NOT NULL,
    resolved_at         TIMESTAMP WITH TIME ZONE    NULL,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 제약 조건
    CONSTRAINT unique_fingerprint_per_project UNIQUE (fingerprint, project_id),
    CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_issue_project_id ON issue(project_id);
CREATE INDEX idx_issue_assignee_id ON issue(assignee_id);
CREATE INDEX idx_issue_fingerprint ON issue(fingerprint);
CREATE INDEX idx_issue_status ON issue(status);
CREATE INDEX idx_issue_severity ON issue(severity);
CREATE INDEX idx_issue_first_occurred_at ON issue(first_occurred_at DESC);
CREATE INDEX idx_issue_last_occurred_at ON issue(last_occurred_at DESC);

-- 코멘트
COMMENT ON TABLE issue IS '로그 그룹핑 이슈 (ERD 기준)';
COMMENT ON COLUMN issue.id IS 'PK (BIGSERIAL)';
COMMENT ON COLUMN issue.assignee_id IS '담당자 ID';
COMMENT ON COLUMN issue.fingerprint IS 'SHA-256 해시 (이슈 그룹핑 키)';
COMMENT ON COLUMN issue.issue_type IS 'BUG/CRASH/PERFORMANCE/NETWORK/DATA_INCONSISTENCY/SECURITY/PAYMENT/BALANCE/UX/DEPENDENCY/CONFIGURATION/UNKNOWN';
COMMENT ON COLUMN issue.status IS 'TODO/IN_PROGRESS/RESOLVED';
COMMENT ON COLUMN issue.severity IS 'LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN issue.severity_score IS '심각도 점수 (0-100, DM-43)';
COMMENT ON COLUMN issue.error_type IS 'NULL_POINTER/INDEX_OUT_OF_BOUNDS/ILLEGAL_ARGUMENT/TIMEOUT/IO/NETWORK/DATABASE/DEADLOCK/OUT_OF_MEMORY/etc.';
COMMENT ON COLUMN issue.stack_key IS '스택트레이스 핵심 경로 (예: UserService.java:42:validateEmail)';
COMMENT ON COLUMN issue.occurrence_count IS '발생 횟수';
COMMENT ON COLUMN issue.resolution_note IS '해결 방법/원인 기록';
