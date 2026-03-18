package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.ArrayList;
import java.util.List;
import kr.java.documind.domain.archive.vector.model.enums.ExtractedContentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunker {

    private static final int MIN_MEANINGFUL_CHARS = 10;

    private final DocumentTransformer documentTransformer;

    public List<Document> chunk(List<Document> documents) {
        List<Document> textDocs = new ArrayList<>();
        List<Document> tableDocs = new ArrayList<>();

        for (Document doc : documents) {
            String contentType = (String) doc.getMetadata().get("content_type");
            if (ExtractedContentType.TABLE.name().equals(contentType)) {
                tableDocs.add(doc);
            } else if (!ExtractedContentType.IMAGE.name().equals(contentType)) {
                textDocs.add(doc);
            }
        }

        log.info("contentType 분류 - TEXT: {}, TABLE: {}", textDocs.size(), tableDocs.size());

        List<Document> chunks = new ArrayList<>();

        if (!textDocs.isEmpty()) {
            List<Document> textChunks = documentTransformer.apply(textDocs);
            log.info("TEXT 청킹 완료 - {} chunks 생성", textChunks.size());
            chunks.addAll(textChunks);
        }

        chunks.addAll(tableDocs);

        sanitizeNullBytes(chunks);
        filterMeaninglessChunks(chunks);

        return chunks;
    }

    private void sanitizeNullBytes(List<Document> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String text = chunk.getText();
            if (text != null && text.indexOf('\0') >= 0) {
                chunks.set(i, chunk.mutate().text(text.replace("\0", "")).build());
            }
        }
    }

    private void filterMeaninglessChunks(List<Document> chunks) {
        int before = chunks.size();
        chunks.removeIf(doc -> !hasMeaningfulText(doc.getText()));
        int removed = before - chunks.size();
        if (removed > 0) {
            log.info("의미 없는 chunk {} 건 제거 → {} chunks 남음", removed, chunks.size());
        }
    }

    private boolean hasMeaningfulText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String stripped = text.replaceAll("[^\\p{L}\\p{N}]", "");
        return stripped.length() >= MIN_MEANINGFUL_CHARS;
    }
}
