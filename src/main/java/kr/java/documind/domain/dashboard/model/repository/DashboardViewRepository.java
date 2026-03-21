package kr.java.documind.domain.dashboard.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.dashboard.model.entity.DashboardView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardViewRepository extends JpaRepository<DashboardView, UUID> {

    List<DashboardView> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    Optional<DashboardView> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<DashboardView> findByProjectIdAndDefaultViewTrue(UUID projectId);
}
