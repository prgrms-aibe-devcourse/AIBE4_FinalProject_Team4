package kr.java.documind.domain.member.model.repository;

import io.lettuce.core.dynamic.annotation.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Project;
import kr.java.documind.domain.member.model.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByPublicId(String publicId);

    List<Project> findByCompanyIdAndStatus(Long companyId, ProjectStatus status);

    List<Project> findByCompanyId(Long companyId);
}
