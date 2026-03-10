package kr.java.documind.domain.archive.vector.service;

import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final VectorStoreManager vectorStoreManager;

    public void deleteBySourceId(Long sourceId) {
        try {
            vectorStoreManager.deleteBySourceId(sourceId);
        } catch (Exception e) {
            log.error("[Vector] 벡터 삭제 실패 - sourceId: {}", sourceId, e);
        }
    }
}
