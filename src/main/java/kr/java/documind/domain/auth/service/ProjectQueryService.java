package kr.java.documind.domain.auth.service;

import java.util.UUID;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {

    private final ProjectRepository projectRepository;

    public String getPublicIdByProjectId(UUID projectId) {
        return projectRepository
                .findPublicIdById(projectId)
                .orElseThrow(ProjectNotFoundException::new);
    }
}
