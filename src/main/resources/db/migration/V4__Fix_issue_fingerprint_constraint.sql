-- V4: issue 테이블의 fingerprint UNIQUE 제약 수정
-- 문제: fingerprint가 전역 UNIQUE로 설정되어 있어, 서로 다른 프로젝트에서 동일한 fingerprint 발생 시 충돌
-- 해결: (fingerprint, project_id) 복합 UNIQUE 제약으로 변경

-- 기존 전역 UNIQUE 제약 삭제
ALTER TABLE issue DROP CONSTRAINT IF EXISTS issue_fingerprint_key;

-- (fingerprint, project_id) 복합 UNIQUE 제약 추가
ALTER TABLE issue ADD CONSTRAINT unique_fingerprint_per_project
    UNIQUE (fingerprint, project_id);

-- 코멘트 추가
COMMENT ON CONSTRAINT unique_fingerprint_per_project ON issue IS
    'Fingerprint는 프로젝트별로 고유해야 함 (전역 UNIQUE 아님)';
