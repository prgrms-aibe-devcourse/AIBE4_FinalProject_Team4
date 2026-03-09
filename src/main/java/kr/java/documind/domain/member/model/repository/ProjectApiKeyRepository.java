package kr.java.documind.domain.member.model.repository;

import java.util.Optional;
import kr.java.documind.domain.member.model.entity.Project;
import kr.java.documind.domain.member.model.entity.ProjectApiKey;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {

    /**
     * 프로젝트의 가장 최근 키 중 REVOKED 상태가 아닌 것(ACTIVE 또는 SUSPENDED)을 반환한다. 프로젝트 설정 페이지 API Key 섹션 표시에 사용.
     */
    Optional<ProjectApiKey> findFirstByProjectAndApiKeyStatusNotOrderByCreatedAtDesc(
            Project project, ApiKeyStatus status);
}
