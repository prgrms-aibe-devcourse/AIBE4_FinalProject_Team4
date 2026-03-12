package kr.java.documind.global.config;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
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
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            VectorStoreDocumentRetriever documentRetriever,
            ContextualQueryAugmenter queryAugmenter) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
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
