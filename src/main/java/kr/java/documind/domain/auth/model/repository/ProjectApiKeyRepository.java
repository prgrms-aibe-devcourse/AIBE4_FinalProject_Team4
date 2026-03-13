package kr.java.documind.domain.auth.model.repository;

import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {
    Optional<ProjectApiKey> findFirstByProjectAndKeyTypeAndApiKeyStatusInOrderByCreatedAtDesc(
            Project project, ApiKeyType keyType, List<ApiKeyStatus> statuses);

    List<ProjectApiKey> findAllByProjectAndApiKeyStatusNot(Project project, ApiKeyStatus status);

    Optional<ProjectApiKey> findByApiKeyHashAndKeyType(String apiKeyHash, ApiKeyType keyType);

    // 프로젝트의 특정 키(INGEST, QUERY)를 폐기 상태로 변경
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE ProjectApiKey pak SET pak.apiKeyStatus = :newStatus, pak.revokedAt = CURRENT_TIMESTAMP "
                    + "WHERE pak.project = :project AND pak.keyType = :keyType AND pak.apiKeyStatus IN :targetStatuses")
    void revokeAllByProjectAndKeyType(
            @Param("project") Project project,
            @Param("keyType") ApiKeyType keyType,
            @Param("newStatus") ApiKeyStatus newStatus,
            @Param("targetStatuses") List<ApiKeyStatus> targetStatuses);
}
