package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.List;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import kr.java.documind.global.enums.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreManager {

    private final VectorStoreRepository vectorStoreRepository;

    @Transactional
    public void insertChunks(Long sourceId, List<Document> chunks, List<float[]> embeddings) {
        vectorStoreRepository.insertChunks(sourceId, chunks, embeddings);
    }

    public void deleteBySourceId(Long sourceId, SourceType sourceType) {
        try {
            vectorStoreRepository.deleteBySourceId(sourceId, sourceType);
        } catch (Exception e) {
            log.error("[Vector] 벡터 삭제 실패 - sourceId: {}, sourceType: {}", sourceId, sourceType, e);
        }
    }
}
