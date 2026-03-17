package kr.java.documind.global.config;

import java.util.List;
import kr.java.documind.domain.archive.rag.DuplicateRemovalPostProcessor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    private static final double SIMILARITY_THRESHOLD = 0.3;
    private static final int TOP_K = 5;

    @Bean
    public VectorStoreDocumentRetriever documentRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .topK(TOP_K)
                .build();
    }

    @Bean
    public ContextualQueryAugmenter queryAugmenter() {
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .documentFormatter(this::formatDocuments)
                .build();
    }

    @Bean
    public DuplicateRemovalPostProcessor duplicateRemovalPostProcessor() {
        return new DuplicateRemovalPostProcessor();
    }

    @Bean
    public DocumentRetriever deduplicatingRetriever(
            VectorStoreDocumentRetriever documentRetriever,
            DuplicateRemovalPostProcessor duplicateRemovalPostProcessor) {
        return query -> {
            List<Document> docs = documentRetriever.retrieve(query);
            return duplicateRemovalPostProcessor.process(query, docs);
        };
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            DocumentRetriever deduplicatingRetriever,
            ContextualQueryAugmenter queryAugmenter) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(deduplicatingRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }

    private String formatDocuments(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(documents.get(i).getText());
            if (i < documents.size() - 1) {
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
