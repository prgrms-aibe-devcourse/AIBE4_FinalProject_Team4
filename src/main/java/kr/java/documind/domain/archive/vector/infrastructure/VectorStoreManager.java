package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.List;
import kr.java.documind.domain.archive.vector.model.repository.VectorStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VectorStoreManager {

    private final VectorStoreRepository vectorStoreRepository;

    @Transactional
    public void insertChunks(Long sourceId, List<Document> chunks, List<float[]> embeddings) {
        vectorStoreRepository.insertChunks(sourceId, chunks, embeddings);
    }

    public void deleteBySourceId(Long sourceId) {
        vectorStoreRepository.deleteBySourceId(sourceId);
    }
}
