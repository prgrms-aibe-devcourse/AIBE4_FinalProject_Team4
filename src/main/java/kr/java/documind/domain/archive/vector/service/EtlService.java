package kr.java.documind.domain.archive.vector.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.event.EmbeddingStatusEvent;
import kr.java.documind.domain.archive.vector.infrastructure.DocumentChunker;
import kr.java.documind.domain.archive.vector.infrastructure.DocumentContentExtractor;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingModelClient;
import kr.java.documind.domain.archive.vector.infrastructure.EmbeddingStatusSseManager;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;
import kr.java.documind.domain.patchnote.infrastructure.DocumentEmbeddedEventPublisher;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.StorageException;
import kr.java.documind.global.storage.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlService {

    private static final Set<String> PDF_METADATA_KEYS =
            Set.of("content_type", "page_number", "total_pages");

    private final DocumentContentExtractor documentContentExtractor;
    private final DocumentChunker documentChunker;
    private final EmbeddingModelClient embeddingModelClient;
    private final VectorStoreManager vectorStoreManager;
    private final FileStore fileStore;

    private final ApplicationEventPublisher eventPublisher;
    private final EmbeddingStatusSseManager embeddingStatusSseManager;
    private final DocumentEmbeddedEventPublisher documentEmbeddedEventPublisher;

    public SseEmitter subscribeEmbeddingStatus(Long sourceId, EmbeddingStatus currentStatus) {
        return embeddingStatusSseManager.register(sourceId, currentStatus);
    }

    public void process(
            UUID projectId, Long sourceId, String storedKey, boolean excludeFromPatchNote) {
        Path tempFilePath = null;
        try {
            changeStatus(sourceId, EmbeddingStatus.PROCESSING);
            tempFilePath = createTempFile(storedKey);

            List<Document> documents = documentContentExtractor.extract(tempFilePath);
            List<Document> chunks = documentChunker.chunk(documents);

            if (chunks.isEmpty()) {
                log.warn("[ETL] 추출된 텍스트가 없습니다 - sourceId: {}", sourceId);
                changeStatus(sourceId, EmbeddingStatus.FAILED);
                return;
            }

            enrichMetadata(chunks, projectId, sourceId);

            List<String> texts = chunks.stream().map(Document::getText).toList();
            List<float[]> embeddings = embeddingModelClient.embed(texts);
            vectorStoreManager.insertChunks(sourceId, chunks, embeddings);

            changeStatus(sourceId, EmbeddingStatus.SUCCESS);

            documentEmbeddedEventPublisher.publishDocumentEmbeddedEvent(
                    sourceId, excludeFromPatchNote);

        } catch (Exception e) {
            log.error("[ETL] 벡터화 실패 - sourceId: {}", sourceId, e);
            cleanupVectors(sourceId);
            changeStatus(sourceId, EmbeddingStatus.FAILED);
        } finally {
            deleteTempFile(tempFilePath);
        }
    }

    private void enrichMetadata(List<Document> chunks, UUID projectId, Long sourceId) {
        int totalChunks = chunks.size();
        for (int i = 0; i < totalChunks; i++) {
            Map<String, Object> metadata = chunks.get(i).getMetadata();
            metadata.keySet().retainAll(PDF_METADATA_KEYS);
            metadata.put("project_id", projectId);
            metadata.put("source_id", sourceId);
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", totalChunks);
            metadata.put("source_type", "DOCUMENT");
        }
    }

    private void changeStatus(Long sourceId, EmbeddingStatus status) {
        eventPublisher.publishEvent(new EmbeddingStatusEvent(sourceId, status));
        embeddingStatusSseManager.send(sourceId, status);
    }

    private void cleanupVectors(Long sourceId) {
        vectorStoreManager.deleteBySourceId(sourceId, SourceType.DOCUMENT);
    }

    private Path createTempFile(String storedKey) {
        Resource resource = fileStore.load(storedKey);
        String suffix = resolveSuffix(storedKey);

        try (InputStream inputStream = resource.getInputStream()) {
            Path tempFilePath = Files.createTempFile("documind-etl-", suffix);
            Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            return tempFilePath;
        } catch (IOException e) {
            throw new StorageException("임시 파일 생성에 실패했습니다.", e);
        }
    }

    private String resolveSuffix(String storedKey) {
        int lastDot = storedKey.lastIndexOf('.');
        return lastDot >= 0 ? storedKey.substring(lastDot) : ".tmp";
    }

    private void deleteTempFile(Path tempFilePath) {
        if (tempFilePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFilePath);
        } catch (IOException e) {
            log.warn("[ETL] 임시 파일 삭제 실패: {}", tempFilePath, e);
        }
    }
}
