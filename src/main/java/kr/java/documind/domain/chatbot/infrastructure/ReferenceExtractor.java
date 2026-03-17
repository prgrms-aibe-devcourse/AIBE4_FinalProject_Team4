package kr.java.documind.domain.chatbot.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.java.documind.domain.archive.document.infrastructure.DocumentMetadataManager;
import kr.java.documind.domain.archive.document.model.entity.DocumentMetadata;
import kr.java.documind.domain.chatbot.model.dto.response.ReferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferenceExtractor {

    private final DocumentMetadataManager documentMetadataManager;

    public List<ReferenceResponse> extract(ChatClientResponse response) {
        List<Document> docs = extractDocuments(response);
        if (docs.isEmpty()) {
            return List.of();
        }
        return buildReferences(docs);
    }

    @SuppressWarnings("unchecked")
    private List<Document> extractDocuments(ChatClientResponse response) {
        if (response.context() == null) {
            return List.of();
        }
        List<Document> docs =
                (List<Document>)
                        response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return docs != null ? docs : List.of();
    }

    private List<ReferenceResponse> buildReferences(List<Document> documents) {
        Set<Long> sourceIds =
                documents.stream()
                        .map(doc -> parseMetadata(doc, "source_id", Long::parseLong))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, DocumentMetadata> metadataMap = documentMetadataManager.findMapByIds(sourceIds);

        List<ReferenceResponse> references = new ArrayList<>();

        for (Document doc : documents) {
            Long sourceId = parseMetadata(doc, "source_id", Long::parseLong);
            if (sourceId == null) {
                continue;
            }

            DocumentMetadata metadata = metadataMap.get(sourceId);
            if (metadata == null) {
                continue;
            }

            Integer pageNumber = parseMetadata(doc, "page_number", Integer::parseInt);
            String chunkText = doc.getText() != null ? doc.getText() : "";

            references.add(
                    new ReferenceResponse(
                            sourceId,
                            metadata.getDocumentName(),
                            metadata.getExtension(),
                            metadata.getVersionString(),
                            pageNumber,
                            chunkText));
        }
        return references;
    }

    private <T> T parseMetadata(Document doc, String key, Function<String, T> parser) {
        Object value = doc.getMetadata().get(key);
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
