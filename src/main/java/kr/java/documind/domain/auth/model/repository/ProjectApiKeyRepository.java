package kr.java.documind.domain.auth.model.repository;

import java.util.List;
import java.util.Optional;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {

    Optional<ProjectApiKey> findFirstByProjectAndApiKeyStatusNotOrderByCreatedAtDesc(
            Project project, ApiKeyStatus status);

    List<ProjectApiKey> findAllByProjectAndApiKeyStatusNot(Project project, ApiKeyStatus status);

    Optional<ProjectApiKey> findByApiKeyHash(String apiKeyHash);
}
