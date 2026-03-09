package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingModelClient {

    private final EmbeddingModel embeddingModel;

    public EmbeddingModelClient(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<float[]> embed(List<String> texts) {
        return embeddingModel.embed(texts);
    }
}
