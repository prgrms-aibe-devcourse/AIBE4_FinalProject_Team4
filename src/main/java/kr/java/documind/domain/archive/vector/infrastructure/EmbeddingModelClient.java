package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmbeddingModelClient {

    private static final int BATCH_SIZE = 50;
    private static final long BATCH_DELAY_MS = 5_000;

    private final EmbeddingModel embeddingModel;

    public EmbeddingModelClient(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<float[]> embed(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            if (i > 0) {
                sleep(BATCH_DELAY_MS);
            }
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            allEmbeddings.addAll(embeddingModel.embed(batch));
            log.debug(
                    "임베딩 배치 완료 - {}/{}",
                    Math.min(i + BATCH_SIZE, texts.size()),
                    texts.size());
        }

        return allEmbeddings;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
