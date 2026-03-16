-- =====================================================================
-- V12: comment 테이블 생성 (멘션 및 알림 기능 대비)
-- =====================================================================

CREATE TABLE comment (
    id BIGSERIAL NOT NULL,
    issue_id BIGINT NOT NULL,
    member_id UUID NOT NULL,
    content TEXT NOT NULL,
    mentioned_member_ids JSONB NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

-- 인덱스: 이슈별 댓글 조회 성능 최적화
CREATE INDEX idx_comment_issue_id ON comment (issue_id);

-- 인덱스: 사용자별 댓글 조회 (필요시)
CREATE INDEX idx_comment_member_id ON comment (member_id);

-- 인덱스: 최신순 정렬 최적화
CREATE INDEX idx_comment_created_at ON comment (created_at DESC);

-- 인덱스: 멘션된 사용자 검색 (GIN 인덱스)
CREATE INDEX idx_comment_mentioned_member_ids ON comment USING GIN (mentioned_member_ids jsonb_path_ops);

-- 코멘트 추가
COMMENT ON TABLE comment IS '이슈 댓글 테이블 (협업 커뮤니케이션 + 멘션 기능)';
COMMENT ON COLUMN comment.issue_id IS '이슈 ID (FK to issue)';
COMMENT ON COLUMN comment.member_id IS '작성자 ID (FK to member)';
COMMENT ON COLUMN comment.content IS '댓글 내용 (멘션 @닉네임 포함 가능)';
COMMENT ON COLUMN comment.mentioned_member_ids IS '멘션된 사용자 UUID 배열 (알림 발송용, 파싱 결과 저장)';
COMMENT ON COLUMN comment.created_at IS '댓글 작성 시각 (UTC)';
COMMENT ON COLUMN comment.updated_at IS '댓글 수정 시각 (UTC)';
