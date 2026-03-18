package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

@Slf4j
public class LineOverlapTextSplitter implements DocumentTransformer {

    private final int maxChars;
    private final int overlapLines;

    public LineOverlapTextSplitter(int maxChars, int overlapLines) {
        this.maxChars = maxChars;
        this.overlapLines = overlapLines;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            result.addAll(splitDocument(doc));
        }
        log.debug(
                "LineOverlapTextSplitter: {} document(s) → {} chunk(s)",
                documents.size(),
                result.size());
        return result;
    }

    private List<Document> splitDocument(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        text = normalizeBlankLines(text);

        String[] lines = text.split("\n", -1);
        List<Document> chunks = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        int currentLength = 0;

        for (String line : lines) {
            int lineLength = line.length() + 1;

            if (line.length() > maxChars) {
                if (!currentLines.isEmpty()) {
                    chunks.add(createChunk(currentLines, document));
                    currentLines = getOverlapLines(currentLines);
                    currentLength = calculateLength(currentLines);
                }
                chunks.add(createChunk(List.of(line), document));
                continue;
            }

            if (currentLength + lineLength > maxChars && !currentLines.isEmpty()) {
                chunks.add(createChunk(currentLines, document));
                currentLines = getOverlapLines(currentLines);
                currentLength = calculateLength(currentLines);
            }

            currentLines.add(line);
            currentLength += lineLength;
        }

        if (!currentLines.isEmpty()) {
            String remaining = String.join("\n", currentLines).trim();
            if (!remaining.isEmpty()) {
                chunks.add(createChunk(currentLines, document));
            }
        }

        return chunks;
    }

    private String normalizeBlankLines(String text) {
        return text.replaceAll("(\\s*\n){3,}", "\n\n");
    }

    private List<String> getOverlapLines(List<String> lines) {
        if (overlapLines <= 0 || lines.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, lines.size() - overlapLines);
        return new ArrayList<>(lines.subList(start, lines.size()));
    }

    private int calculateLength(List<String> lines) {
        if (lines.isEmpty()) {
            return 0;
        }
        return lines.stream().mapToInt(String::length).sum() + lines.size();
    }

    private Document createChunk(List<String> lines, Document source) {
        String content = String.join("\n", lines).trim();
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        return new Document(content, metadata);
    }
}
