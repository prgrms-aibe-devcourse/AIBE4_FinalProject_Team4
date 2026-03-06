package kr.java.documind.domain.member.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByPublicId(String publicId);
}
