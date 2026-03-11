-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- V9: IssueHistory 테이블 생성
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 목적: 이슈 변경 이력 추적 (담당자, 상태, 우선순위 변경 기록)
-- 관련 기능:
--   - DM-44: 이슈 대시보드 (담당자 할당, 상태 전환, 이력 조회)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- IssueHistory 테이블 생성
CREATE TABLE issue_history (
    id              BIGSERIAL                   PRIMARY KEY,
    issue_id        BIGINT                      NOT NULL,
    modifier_id     UUID                        NOT NULL,
    field_name      VARCHAR(50)                 NOT NULL,
    before_value    TEXT                        NULL,
    after_value     TEXT                        NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 제약 조건
    CONSTRAINT fk_issue_history_issue FOREIGN KEY (issue_id) REFERENCES issue(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_issue_history_issue_id ON issue_history(issue_id);
CREATE INDEX idx_issue_history_created_at ON issue_history(created_at DESC);

-- 코멘트
COMMENT ON TABLE issue_history IS '이슈 변경 이력 (담당자, 상태, 우선순위 등)';
COMMENT ON COLUMN issue_history.id IS 'PK (BIGSERIAL)';
COMMENT ON COLUMN issue_history.issue_id IS '이슈 ID (FK)';
COMMENT ON COLUMN issue_history.modifier_id IS '변경자 ID';
COMMENT ON COLUMN issue_history.field_name IS '변경된 필드명 (STATUS/ASSIGNEE/PRIORITY)';
COMMENT ON COLUMN issue_history.before_value IS '변경 전 값';
COMMENT ON COLUMN issue_history.after_value IS '변경 후 값';
