package kr.java.documind.domain.member.model.repository;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.member.model.enums.AccountStatus;

public interface ProjectMemberRepositoryCustom {

    /**
     * 프로젝트 활성 멤버 ID 목록 조회 (배치 조회)
     *
     * @param projectId 프로젝트 ID
     * @param memberIds 확인할 멤버 ID 목록
     * @param status 계정 상태
     * @return 유효한 멤버 ID 목록
     */
    List<UUID> findValidMemberIds(UUID projectId, List<UUID> memberIds, AccountStatus status);
}
