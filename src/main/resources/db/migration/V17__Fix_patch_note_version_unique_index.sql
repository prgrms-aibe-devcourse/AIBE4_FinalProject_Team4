-- V17: patch_note 버전 unique constraint를 partial index로 교체
-- soft delete된 버전과의 unique 충돌 방지 (deleted_at IS NULL 조건 추가)
--
-- 기존 uk_patch_note_project_version은 deleted_at 없이 (project_id, major, minor, patch)에
-- UNIQUE 제약을 걸기 때문에, 동일 버전을 soft delete 후 재생성할 때 중복 키 오류가 발생한다.
-- Partial index로 교체하면 deleted_at IS NULL인 행에만 유니크 조건이 적용되어
-- soft delete된 버전과 새 버전이 공존할 수 있다.

ALTER TABLE patch_note
    DROP CONSTRAINT uk_patch_note_project_version;

CREATE UNIQUE INDEX uk_patch_note_project_version
    ON patch_note (project_id, major_version, minor_version, patch_version)
    WHERE deleted_at IS NULL;
